-- =============================================================================
-- 001_dispatch_and_incident_classification.sql
-- Target: production PostgreSQL (Neon). NOT run automatically. A human runs it.
--
-- RUN ORDER
--   1. Run PART A (pre-deploy) first. It is safe to re-run.
--   2. Deploy / start the new build once. Hibernate (ddl-auto=update) creates the new
--      tables: incident_staff_assignments, incident_item_assignments,
--      incident_alarm_events, and adds the remaining nullable columns.
--   3. Run PART B (copy old deployments as released history, then drop the old table).
--
-- Before running, take a backup / Neon branch. Verify table/column names against the
-- live schema; the names below assume Spring's default snake_case naming.
-- =============================================================================


-- =============================================================================
-- PART A: PRE-DEPLOY
-- =============================================================================

BEGIN;

-- A0. Inspect existing CHECK constraints (informational; run separately if you want to look).
-- SELECT conrelid::regclass AS tbl, conname, pg_get_constraintdef(oid)
-- FROM pg_constraint
-- WHERE contype = 'c' AND conrelid::regclass::text IN ('incidents','items','staff');

-- A1. Hibernate may have generated CHECK (col IN (...)) constraints for enum columns.
--     The old status check only allows ONGOING/PENDING and would reject RESOLVED, and
--     ddl-auto=update never repairs constraints. Drop every CHECK on incidents.status
--     and incidents.severity_level (they are re-created by Hibernate only on new tables).
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT c.conname
    FROM pg_constraint c
    WHERE c.contype = 'c'
      AND c.conrelid = 'incidents'::regclass
      AND (pg_get_constraintdef(c.oid) ILIKE '%status%'
           OR pg_get_constraintdef(c.oid) ILIKE '%severity_level%')
  LOOP
    EXECUTE format('ALTER TABLE incidents DROP CONSTRAINT %I', r.conname);
  END LOOP;
END $$;

-- A2. PENDING no longer exists in the enum; Hibernate cannot read such rows.
--     Must run BEFORE the new build starts.
UPDATE incidents SET status = 'ONGOING' WHERE status = 'PENDING';

-- A3. New NOT NULL columns on tables that already contain rows need defaults,
--     otherwise Hibernate's own "add column ... not null" fails.
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS nature varchar(255) NOT NULL DEFAULT 'EMERGENCY';
ALTER TABLE items     ADD COLUMN IF NOT EXISTS category varchar(255) NOT NULL DEFAULT 'OTHER';
ALTER TABLE items     ADD COLUMN IF NOT EXISTS quantity_dispatched integer NOT NULL DEFAULT 0;

-- A4. severity_level is now optional (deprecated).
ALTER TABLE incidents ALTER COLUMN severity_level DROP NOT NULL;

-- A5. Only if A0 showed conflicting CHECKs on these columns, drop them the same way:
--   ALTER TABLE staff DROP CONSTRAINT <name>;   -- staff_availability
--   ALTER TABLE items DROP CONSTRAINT <name>;   -- item_condition / category

COMMIT;


-- =============================================================================
-- PART B: POST-DEPLOY (after the new build has started once)
-- =============================================================================
-- Default assumption: existing deployment data is disposable dev data. Each old
-- incident_deployed_staff row becomes a RELEASED historical assignment. Staff
-- availability is NOT touched. If the data matters, adapt this before running.
--
-- Verify first that the new tables exist and their column names:
--   \d incident_staff_assignments

BEGIN;

INSERT INTO incident_staff_assignments (incident_id, staff_id, assigned_at, released_at)
SELECT d.incident_id,
       d.staff_id,
       COALESCE(i.date_time_reported, now()),
       now()
FROM incident_deployed_staff d
JOIN incidents i ON i.id = d.incident_id;

DROP TABLE incident_deployed_staff;

COMMIT;

-- Optional sanity check afterwards:
--   SELECT count(*) FROM incident_staff_assignments WHERE released_at IS NOT NULL;
