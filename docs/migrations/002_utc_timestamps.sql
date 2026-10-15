-- =============================================================================
-- 002_utc_timestamps.sql
-- Target: production PostgreSQL (Neon). NOT run automatically. A human runs it, ONCE.
--
-- Why: the server now runs in UTC (BumberoosApplication pins the JVM time zone), so every
-- LocalDateTime is stored as UTC. Rows written before this change were stored in the time
-- zone of the machine that ran the server. This script converts them to UTC.
--
-- ASSUMPTION: the old server ran in Philippine time (UTC+8). If it ran in another zone,
-- change the interval below. If old data is disposable, skip this script.
--
-- RUN ORDER
--   1. Take a backup / Neon branch.
--   2. Run this script ONCE, immediately before starting the UTC build. Running it twice
--      shifts the times by another 8 hours.
-- Column names assume Spring's default snake_case naming.
-- =============================================================================

BEGIN;

UPDATE incidents SET
  date_time_reported = date_time_reported - interval '8 hours',
  under_control_at   = under_control_at   - interval '8 hours',
  fire_out_at        = fire_out_at        - interval '8 hours',
  resolved_at        = resolved_at        - interval '8 hours';

UPDATE incident_staff_assignments SET
  assigned_at = assigned_at - interval '8 hours',
  released_at = released_at - interval '8 hours';

UPDATE incident_item_assignments SET
  assigned_at = assigned_at - interval '8 hours',
  released_at = released_at - interval '8 hours';

UPDATE incident_alarm_events SET raised_at = raised_at - interval '8 hours';

UPDATE user_accounts SET created_at = created_at - interval '8 hours';

COMMIT;
