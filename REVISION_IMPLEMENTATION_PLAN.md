# BumberoOS — Implementation Plan: Dispatch, Status Sync, and Incident Classification

**Audience:** the coding agent working in the `bumberoos` repo (branch `migrationSQL`).
**Read first:** `system_docs.md` (describes the code as it exists today). This plan builds on it and changes parts of it.
**Stack reminder:** Java 17, Spring Boot 4.1.1 (webmvc, Data JPA, Validation), PostgreSQL (Neon) at runtime, H2 (PostgreSQL mode) in tests, Lombok, hand-written mappers, base package `com.groupnine.bumberoos`.

---

## 0. How to work

1. Work **phase by phase, in order** (Section 6). Make one commit per phase with a clear message. Do not start a phase until the previous phase's acceptance criteria pass.
2. After each phase run `./mvnw test` (if `Permission denied`, use `sh mvnw test`). Do not move on with failing tests.
3. Follow the existing conventions in Section 2. Do not refactor unrelated code.
4. When this plan is ambiguous, pick the **default** stated in Section 9, keep going, and list every assumption you made in your final summary.
5. Never commit `.env` or credentials. Never run schema-changing SQL against Neon yourself: write the migration script (Section 5) and tell the human to run it.
6. At the end, update `system_docs.md` (Phase 6) so it matches the new code.

---

## 1. Goal and scope

The system is a back office for a **volunteer fire brigade** in the Philippines. Three features are required:

**F1. Assign staff and items when creating an incident, and modify assignments later.**
Items are engines, first-aid kits, SCBAs, fire suits, hoses, etc. A dispatcher must be able to send *more* staff and equipment to an ongoing incident, and pull some back.

**F2. Status sync.**
While assigned to an incident, staff and items are `DISPATCHED`. When the incident is resolved (or an assignment is released), they return to `AVAILABLE`.

**F3. Incident classification.**
- Every incident is **EMERGENCY** or **NON_EMERGENCY**.
- Every incident is **ONGOING** or **RESOLVED**.
- Emergency incidents may carry a BFP **alarm level** (first alarm … Task Force … General Alarm), with history of escalations.

### Real-world context that drove the design
- The BFP (government fire service) declares alarm levels; a volunteer brigade **records** them, it does not declare them. Alarm levels escalate in minutes and are reported with timestamps.
- BFP distinguishes "fire under control" from "fire out". We keep optional timestamps for both, but the system status is only ONGOING/RESOLVED to keep dispatcher workflow simple.
- Volunteer brigades also do non-emergency work (water rationing, standby, drills), hence NON_EMERGENCY.
- DILG Memorandum Circular 2008-090 requires accredited brigades to keep personnel/truck lists current, notify BFP when responding, and submit an After Operations Report within 3 days. (Those are **out of scope** here; see backlog in Section 7.)

### Explicitly out of scope
Authentication/authorisation, pagination, structured location/geo, volunteer credential tracking, casualty reporting, After Operations Report generation, SMS/push alerts. These are listed as backlog in Section 7. Do not build them now.

---

## 2. Conventions to follow (from `system_docs.md`)

- Entities never cross the API boundary: controllers take `*Request` records and return `*Response` records (Java records in `dto`).
- Services work with entities and return `Optional<Entity>` for "might not exist" and `boolean` for removes. Controllers turn empty results into `404` via `ResponseStatusException`. **Exception:** the new `DispatchService` (Section 4.3) throws unchecked domain exceptions instead, because one call can fail for several different reasons.
- IDs are server-generated (`GenerationType.IDENTITY`); services null client-sent ids on create.
- `PATCH` semantics: `null`/omitted field = unchanged.
- Normalisation lives in entities (`normalize()` via constructor and `@PrePersist`/`@PreUpdate`) using `StringUtil`.
- Services are class-level `@Transactional`, reads `readOnly = true`.
- Lombok `@Getter`, `@Setter`, `@NoArgsConstructor` on entities. Hand-written mappers (`@Component`), no MapStruct.
- Errors: `ApiExceptionHandler` returns `{"error": "<message>"}`.
- Tests: Mockito unit tests (`*MockTest`) and Spring + H2 integration tests per service, plus `MappersTest`.

---

## 3. Design decisions (already made — do not re-litigate)

| # | Decision | Reason |
|---|---|---|
| D1 | Replace the `@ManyToMany incident_deployed_staff` with assignment entities `IncidentStaffAssignment` and `IncidentItemAssignment`. | Need per-assignment timestamps, roles, quantities, and release-without-delete history. |
| D2 | Release an assignment by setting `releasedAt`; never delete assignment rows during normal operation. | History of who/what was on scene; feeds future reports. |
| D3 | **No separate Apparatus entity.** Engines are `ItemEntity` rows with `category = ENGINE`. | Simpler; user wants engines treated as items. |
| D4 | Items are **quantity-based**: `quantity` (total owned) and `quantityDispatched` (currently out). Available = `quantity - quantityDispatched`. Individually tracked gear (engines, ambulances, SCBA sets) should be entered as separate rows with `quantity = 1`. | Handles both "1 engine" and "10 fire suits" with one model. |
| D5 | Item availability in responses is **derived**, not stored (see 4.1). Staff availability stays the stored `StaffAvailability` enum. | Avoids drift between quantity fields and a status column. |
| D6 | `IncidentStatus` becomes `ONGOING`, `RESOLVED` (remove `PENDING`). New incidents are always `ONGOING`; clients cannot set status on create or PATCH. Status changes only via `POST /{id}/resolve` and `POST /{id}/reopen`. | Prevents bypassing the release logic. |
| D7 | New enum `IncidentNature { EMERGENCY, NON_EMERGENCY }`, required on create. | Feature F3. |
| D8 | `IncidentSeverityLevel` stays but becomes **optional and `@Deprecated`** (nullable column, optional in DTOs). `AlarmLevel` is the BFP-aligned measure. | Non-breaking now; remove later. |
| D9 | `alarmLevel` is only valid when `nature = EMERGENCY`. Every alarm change appends an `IncidentAlarmEvent`. | Alarm levels escalate with timestamps. |
| D10 | Orchestration of assign/release/resolve lives in a new `DispatchService`. `StaffService` and `InventoryService` gain lock-and-load methods. | `IncidentService` keeps CRUD; avoids a service wrapping three repositories. |
| D11 | Concurrency: pessimistic write locks (`@Lock(PESSIMISTIC_WRITE)`) on incident, staff and item rows when changing assignments. Lock order: incident first, then staff/items in **ascending id order**. | Two dispatchers must not send the same person/engine at once; consistent order avoids deadlocks. |
| D12 | Timestamps use `LocalDateTime` (consistent with existing `dateTimeReported`). | Consistency; timezone handling is a backlog item. |

---

## 4. Target design

### 4.1 Domain model

#### New / changed enums (`domain.enums`)
```java
public enum IncidentNature { EMERGENCY, NON_EMERGENCY }

public enum IncidentStatus { ONGOING, RESOLVED }          // PENDING removed

public enum AlarmLevel {                                    // ordered as the BFP escalation ladder
    FIRST_ALARM, SECOND_ALARM, THIRD_ALARM, FOURTH_ALARM, FIFTH_ALARM,
    TASK_FORCE_ALPHA, TASK_FORCE_BRAVO, TASK_FORCE_CHARLIE, TASK_FORCE_DELTA,
    TASK_FORCE_ECHO, TASK_FORCE_HOTEL, TASK_FORCE_INDIA,
    GENERAL_ALARM
}

public enum ItemCategory {
    ENGINE, AMBULANCE, FIRST_AID, SCBA, FIRE_SUIT, HOSE, RESCUE_TOOL, OTHER
}

public enum ItemAvailability {                              // response-only, derived
    AVAILABLE, PARTIALLY_DISPATCHED, DISPATCHED, UNAVAILABLE
}
```
`StaffAvailability` (`UNAVAILABLE`, `AVAILABLE`, `DISPATCHED`) and `ItemCondition` (`GOOD`, `DAMAGED`, `RETIRED`) are unchanged. `IncidentSeverityLevel` stays, marked `@Deprecated`.

#### `IncidentEntity` changes
- Add `nature` (`IncidentNature`, `@Enumerated(STRING)`, not null).
- `status`: still `IncidentStatus`, not null, default `ONGOING`.
- Add `alarmLevel` (`AlarmLevel`, nullable).
- `severityLevel`: make **nullable**.
- Add nullable `LocalDateTime underControlAt`, `fireOutAt`, `resolvedAt`.
- **Remove** the `deployedStaff` `@ManyToMany` and any helper methods built on it.
- Add `@OneToMany(mappedBy = "incident", cascade = ALL, orphanRemoval = true)` lists: `staffAssignments`, `itemAssignments`, `alarmEvents` (all lazy).
- Normalisation unchanged for `incidentType`, `reportedBy`, `location`.

#### `ItemEntity` changes
- Add `category` (`ItemCategory`, not null, default `OTHER`).
- Add `serialNumber` (String, nullable; plate number for engines, serial for SCBA).
- Add `quantityDispatched` (`int`, not null, default 0).
- Invariant, enforced in the service layer: `0 <= quantityDispatched <= quantity`.
- Add a transient helper `getQuantityAvailable()` = `quantity - quantityDispatched`.
- **Derived availability** (put it in `ItemMapper` or a method on the entity):
  - `condition != GOOD` or `quantity == 0` → `UNAVAILABLE`
  - `quantityDispatched == 0` → `AVAILABLE`
  - `quantityDispatched == quantity` → `DISPATCHED`
  - otherwise → `PARTIALLY_DISPATCHED`

#### `StaffEntity`
No new columns. The `availability` field and enum remain. Behaviour rules are in Section 4.4.

#### New entities (`domain.entities`)

`IncidentStaffAssignment` (table `incident_staff_assignments`)
| Field | Notes |
|---|---|
| `id` | identity PK |
| `incident` | `@ManyToOne(LAZY)`, not null |
| `staff` | `@ManyToOne(LAZY)`, not null |
| `role` | nullable String (e.g. "Driver", "Nozzleman"); trimmed, Title Case |
| `assignedAt` | `LocalDateTime`, not null, set on create |
| `releasedAt` | nullable; `null` means active |

`IncidentItemAssignment` (table `incident_item_assignments`)
| Field | Notes |
|---|---|
| `id` | identity PK |
| `incident` | `@ManyToOne(LAZY)`, not null |
| `item` | `@ManyToOne(LAZY)`, not null |
| `quantity` | `int`, `>= 1`, units of this item currently/previously sent |
| `assignedAt` | not null |
| `releasedAt` | nullable; `null` means active |
| `returnCondition` | `ItemCondition`, nullable; set when returned |

`IncidentAlarmEvent` (table `incident_alarm_events`)
| Field | Notes |
|---|---|
| `id` | identity PK |
| `incident` | `@ManyToOne(LAZY)`, not null |
| `alarmLevel` | `AlarmLevel`, not null |
| `raisedAt` | `LocalDateTime`, not null |

Add an `isActive()` helper (`releasedAt == null`) on both assignment entities.

#### Repositories (`repositories`)
- `IncidentStaffAssignmentRepository`: `findByIncidentIdAndStaffIdAndReleasedAtIsNull`, `findByIncidentIdAndReleasedAtIsNull`, `existsByStaffId`, `existsByStaffIdAndReleasedAtIsNull`.
- `IncidentItemAssignmentRepository`: same patterns by `itemId`.
- `IncidentAlarmEventRepository`.
- Add to `IncidentRepository`, `StaffRepository`, `ItemRepository`:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select x from <Entity> x where x.id = :id")
  Optional<<Entity>> findByIdForUpdate(@Param("id") Long id);
  ```
- Add list queries for filters: `IncidentRepository` by `status` and/or `nature`; `StaffRepository` by `availability`; `ItemRepository` by `category` and by "has available units" (`quantity > quantityDispatched and itemCondition = GOOD`).

### 4.2 Item and staff rules summary

| Action | Staff | Item |
|---|---|---|
| Assign | requires `employed == true` and `availability == AVAILABLE`; becomes `DISPATCHED` | requires `condition == GOOD` and `quantityAvailable >= requested`; `quantityDispatched += requested` |
| Release | becomes `AVAILABLE` | `quantityDispatched -= qty` |
| Resolve incident | all active assignments released, staff → `AVAILABLE` | all active assignments released, quantities returned, optional return condition applied |

### 4.3 Services

#### `DispatchService` (new, `@Transactional`)
Wraps `IncidentRepository` and the two assignment repositories; uses `StaffService.findByIdForUpdate` and `InventoryService.findByIdForUpdate` (add these; they call the repositories' `findByIdForUpdate`).

Public methods (all load the incident with `findByIdForUpdate` first):

1. **`createIncident(IncidentEntity incident, Collection<Long> staffIds, Collection<ItemRequestLine> items)`**
   Persist the incident (`status = ONGOING`, null id), set `dateTimeReported` default, append an alarm event if `alarmLevel != null`, then perform the same logic as `assignStaff` / `assignItem` for each requested id. **All-or-nothing:** any failure throws and rolls back the whole transaction. De-duplicate `staffIds`; merge duplicate `itemId` lines by summing quantities.
2. **`assignStaff(Long incidentId, Long staffId, String role)`**
   - Incident not found → `ResourceNotFoundException` (404); staff not found → same.
   - Incident not `ONGOING` → `ConflictException` (409).
   - If an active assignment already exists for (incident, staff): idempotent no-op (update `role` if provided) and return.
   - Staff must be `employed` and `AVAILABLE`, else `ConflictException` with a message naming the reason (e.g. `"Juan Cruz is DISPATCHED"`, `"Juan Cruz is not employed"`).
   - Create assignment (`assignedAt = now`), set staff `DISPATCHED`.
3. **`releaseStaff(Long incidentId, Long staffId)`**
   Find the active assignment; if none, no-op. Else set `releasedAt = now`, staff → `AVAILABLE`. Allowed on ONGOING incidents only (on RESOLVED, there is nothing active anyway).
4. **`setItemQuantity(Long incidentId, Long itemId, int quantity)`** (PUT semantics: `quantity` = total units of this item that should be on this incident)
   - `quantity < 1` → 400 (validation). Incident must be `ONGOING` (409).
   - No active assignment: create one. Require item `condition == GOOD` and `quantityAvailable >= quantity`, else 409.
   - Active assignment exists with quantity `q`: `delta = quantity - q`.
     - `delta > 0`: require item `GOOD` and `quantityAvailable >= delta`; increase assignment and `quantityDispatched`.
     - `delta < 0`: partial return; decrease both.
     - `delta == 0`: no-op.
5. **`releaseItem(Long incidentId, Long itemId)`**
   Release the active assignment (`releasedAt = now`), `quantityDispatched -= qty`. No active assignment → no-op.
6. **`resolve(Long incidentId, List<ItemReturn> itemReturns)`**
   - Already `RESOLVED` → no-op (idempotent), return the incident.
   - For each active staff assignment: lock staff (ascending id), set `releasedAt = now`, staff → `AVAILABLE`.
   - For each active item assignment: lock item (ascending id), set `releasedAt = now`, `quantityDispatched -= qty` (assert result `>= 0`), and if `itemReturns` has an entry for the item set `returnCondition` and the item's `condition` (`GOOD`/`DAMAGED`/`RETIRED`). A non-`GOOD` condition means the item is `UNAVAILABLE` until someone fixes it with a PATCH. `itemReturns` entries for items not on this incident → 400/409.
   - Set `status = RESOLVED`, `resolvedAt = now`.
   - Known limitation to document: return condition applies to the whole item row. Bulk gear that needs per-unit condition should be tracked as individual rows.
7. **`reopen(Long incidentId)`**
   `RESOLVED → ONGOING`, clear `resolvedAt`. Does **not** re-dispatch anyone. Already `ONGOING` → no-op.
8. **`deleteIncident(Long incidentId)`**
   If `ONGOING`, run the same release logic as `resolve` first (so staff/items return to available), then delete the incident (cascade deletes its assignments and alarm events). Return `false` if not found.

Lock ordering (D11): `incident` first, then staff or items in ascending id order. Never lock in any other order.

#### `IncidentService` (changed)
- Keep plain CRUD and `PATCH`. Remove the old `deployStaff` / `undeployStaff` methods (this also removes the file's current compile error at line 67; the missing `StaffEntity` import becomes moot — but if you keep any code that uses it, add the import).
- `update(...)` applies: `nature`, `alarmLevel`, `incidentType`, `location`, `reportedBy`, `dateTimeReported`, `underControlAt`, `fireOutAt`, `severityLevel` (nullable, deprecated).
- Validation inside `update`:
  - Cannot update a `RESOLVED` incident's `nature`/`alarmLevel` → 409. (Editing descriptive text such as `location` on a resolved incident is allowed.)
  - If resulting `nature == NON_EMERGENCY` and `alarmLevel` is non-null → clear `alarmLevel` (when nature is being changed) or reject with 400 (when only alarm is being set on a non-emergency incident).
  - When `alarmLevel` changes to a different non-null value, append an `IncidentAlarmEvent(now)`.
- Add list with optional filters `status`, `nature`.

#### `StaffService` (changed)
- `findByIdForUpdate(Long id)` → `Optional<StaffEntity>`.
- `update(...)` new availability rules (throw `ConflictException`):
  - Manually setting `availability = DISPATCHED` is not allowed (only dispatch can do that).
  - Changing availability of a staff member who has an active assignment is not allowed.
  - Setting `employed = false` while `DISPATCHED` is not allowed.
- `delete(...)`: if the staff member has **any** assignment row (active or historical) or is linked to a user account → `ConflictException` (409) with a message suggesting setting `employed = false` instead. This fixes known issue #5.
- Add list-by-availability.

#### `InventoryService` (changed)
- `findByIdForUpdate(Long id)`.
- `addItem`: also accepts `category` and `serialNumber`; new items start with `quantityDispatched = 0`. Keep existing validation (blank name, negative quantity).
- `update(...)`: new `category`/`serialNumber`; reject reducing `quantity` below `quantityDispatched` (409). Allow `condition` changes at any time.
- `increaseQuantity`: reject if the resulting `quantity < quantityDispatched` or `< 0` (fixes known issue #6). Use the same checked/unchecked style the class already uses, but make the HTTP result `400`/`409`.
- `delete(...)`: if the item has **any** assignment row → `ConflictException`, suggesting condition `RETIRED` instead.
- Add list filters: `category`, and `availableOnly=true` (quantity > dispatched, condition `GOOD`).

#### New unchecked exceptions (`domain.exceptions`)
- `ConflictException` → `409`.
- `ResourceNotFoundException` → `404`.
- Both handled in `ApiExceptionHandler` with `{"error": "..."}`.
- Also add a handler for `ResponseStatusException` so existing `404`s use the same `{"error": ...}` shape (fixes known issue #4). Document the changed 404 body.

### 4.4 API

All bodies JSON, base path `/api`. `*` = required.

#### Incidents — `/api/incidents`
| Method | Path | Body / params | Result |
|---|---|---|---|
| GET | `/` | optional `?status=ONGOING|RESOLVED&nature=EMERGENCY|NON_EMERGENCY` | `IncidentResponse[]` |
| GET | `/{id}` | – | `IncidentResponse` |
| POST | `/` | `IncidentRequest` | `201` `IncidentResponse` (atomic create + assign) |
| PATCH | `/{id}` | `IncidentUpdateRequest` | `IncidentResponse` |
| PUT | `/{id}/staff/{staffId}` | optional `?role=Driver` | `IncidentResponse` (idempotent) |
| DELETE | `/{id}/staff/{staffId}` | – | `IncidentResponse` (release; no-op if not active) |
| PUT | `/{id}/items/{itemId}?quantity=N` | `quantity >= 1` | `IncidentResponse` (set total units on this incident) |
| DELETE | `/{id}/items/{itemId}` | – | `IncidentResponse` (release all of that item) |
| POST | `/{id}/resolve` | optional `ResolveRequest` | `IncidentResponse` |
| POST | `/{id}/reopen` | – | `IncidentResponse` |
| DELETE | `/{id}` | – | `204` (releases anything still dispatched) |

Note: the existing `PUT/DELETE /{id}/staff/{staffId}` previously returned an empty/other result; keep paths, now return the updated `IncidentResponse`.

`IncidentRequest`
```json
{
  "nature": "EMERGENCY",              // *
  "alarmLevel": "FIRST_ALARM",        // optional; only when nature=EMERGENCY
  "incidentType": "Fire",             // *
  "location": "Zone 3",               // *
  "reportedBy": "J. Cruz",            // *
  "dateTimeReported": "2026-10-14T09:30:00",   // optional, defaults to now
  "severityLevel": "HIGH",            // optional, deprecated
  "staffIds": [2, 5],                 // optional
  "items": [ { "itemId": 7, "quantity": 1 }, { "itemId": 9, "quantity": 4 } ]   // optional
}
```
Validation: `items[].quantity` `@Min(1)`, `staffIds` elements not null. There is **no `status` field** (D6).

`IncidentUpdateRequest`: any of `nature, alarmLevel, incidentType, location, reportedBy, dateTimeReported, underControlAt, fireOutAt, severityLevel`. No `status`.

`ResolveRequest` (optional body)
```json
{ "itemReturns": [ { "itemId": 7, "condition": "DAMAGED" } ] }
```

`IncidentResponse`
```json
{
  "id": 1, "nature": "EMERGENCY", "status": "ONGOING", "alarmLevel": "SECOND_ALARM",
  "severityLevel": null, "incidentType": "Fire", "location": "Zone 3", "reportedBy": "J. Cruz",
  "dateTimeReported": "...", "underControlAt": null, "fireOutAt": null, "resolvedAt": null,
  "deployedStaffIds": [2, 5],                    // active staff ids; kept for backward compatibility
  "staff": [ { "staffId": 2, "name": "Juan Cruz", "role": "Driver", "assignedAt": "...", "releasedAt": null } ],
  "items": [ { "itemId": 7, "name": "Engine 1", "category": "ENGINE", "quantity": 1, "assignedAt": "...", "releasedAt": null, "returnCondition": null } ],
  "alarmHistory": [ { "alarmLevel": "FIRST_ALARM", "raisedAt": "..." } ]
}
```
`staff` and `items` include both active and released assignments (history); `deployedStaffIds` includes active only.

#### Staff — `/api/staff`
- `GET /?availability=AVAILABLE` (new optional filter), everything else as today.
- `StaffResponse.availability` now reflects dispatch state.

#### Items — `/api/items`
- `GET /?category=ENGINE&availableOnly=true` (new optional filters).
- `ItemRequest` / `ItemUpdateRequest` gain `category`, `serialNumber`.
- `ItemResponse` gains `category`, `serialNumber`, `quantityDispatched`, `quantityAvailable`, `availability` (derived).

### 4.5 Error mapping
| Situation | Status |
|---|---|
| Validation failure | 400 (existing) |
| Incident/staff/item not found | 404 `{"error": ...}` |
| Staff not AVAILABLE / not employed; item not GOOD or insufficient units; incident not ONGOING; deleting staff/items with assignments; quantity below dispatched | 409 |
| `alarmLevel` set on non-emergency incident via PATCH | 400 |

Error messages must be specific and human-readable (name the staff/item and the reason).

---

## 5. Database migration (production Neon DB)

`spring.jpa.hibernate.ddl-auto=update` will **create new tables and add new nullable columns**, but it will **not** drop or rewrite constraints, drop columns, or backfill data. Several things need a manual script. Write it to `docs/migrations/001_dispatch_and_incident_classification.sql` and **do not run it yourself** — hand it to the human with run-order instructions. (Tests use H2 `create-drop` and are unaffected.)

### Likely problems to handle (verify each against the live schema; do not assume)
1. **Enum check constraints.** Recent Hibernate versions generate `CHECK (col IN (...))` for `@Enumerated(STRING)` columns. If `incidents.status` has a check allowing only `ONGOING`/`PENDING`, inserting `RESOLVED` will fail and `ddl-auto=update` will not fix it. Inspect with:
   ```sql
   SELECT conrelid::regclass AS tbl, conname, pg_get_constraintdef(oid)
   FROM pg_constraint WHERE contype = 'c' AND conrelid::regclass::text IN ('incidents','items','staff');
   ```
   Drop and recreate (or just drop) the constraints on `incidents.status`, and on `staff.staff_availability` / `items.item_condition` only if they conflict.
2. **Existing rows with `PENDING` status:**
   `UPDATE incidents SET status = 'ONGOING' WHERE status = 'PENDING';` (run **before** the app deploys with the new enum, or Hibernate cannot read them).
3. **New NOT NULL columns on tables that already have rows** need defaults:
   ```sql
   ALTER TABLE incidents ADD COLUMN IF NOT EXISTS nature varchar(255) NOT NULL DEFAULT 'EMERGENCY';
   ALTER TABLE items ADD COLUMN IF NOT EXISTS category varchar(255) NOT NULL DEFAULT 'OTHER';
   ALTER TABLE items ADD COLUMN IF NOT EXISTS quantity_dispatched integer NOT NULL DEFAULT 0;
   ```
4. **`severity_level` becomes nullable:**
   `ALTER TABLE incidents ALTER COLUMN severity_level DROP NOT NULL;`
5. **Old join table `incident_deployed_staff`.** Default assumption: existing data is disposable dev data. Provide a script that copies each row into `incident_staff_assignments` as a **released** historical assignment (`assigned_at = incidents.date_time_reported`, `released_at = now()`), without touching staff availability, then drops the old table. The new tables must exist first (run the new app build once so Hibernate creates them, then run the copy step). Confirm the exact table and column names Hibernate created before writing the copy statement. If the human says the data matters, adapt the script instead of dropping rows.

State the run order clearly in the script header:
1. Run steps 2–4 (pre-deploy).
2. Deploy/start the new build (creates new tables).
3. Run the copy-and-drop step (step 5).

---

## 6. Phased implementation checklist

### Phase 0 — Baseline (unblock the build)
- [ ] Create a working branch from `migrationSQL`.
- [ ] Fix the missing `import com.groupnine.bumberoos.domain.entities.StaffEntity;` in `services/IncidentService.java` (known issue #1) **only if** you still need that method now; otherwise proceed — Phase 2 removes it.
- [ ] Run `./mvnw test`. Record how many tests pass/fail as the baseline. All pre-existing tests must pass again by the end.
- [ ] Look at the live schema constraints via the Section 5 query **only if the human provides DB access**; otherwise just write the script defensively.

**Acceptance:** project compiles; baseline test result noted.

### Phase 1 — Domain and persistence
- [ ] Add/modify enums (4.1). Remove `PENDING` from `IncidentStatus`; mark `IncidentSeverityLevel` `@Deprecated`.
- [ ] Modify `IncidentEntity`, `ItemEntity`; add the three new entities.
- [ ] Remove the `@ManyToMany deployedStaff` and its helpers.
- [ ] Add repositories and the `findByIdForUpdate` locking queries and filter queries.
- [ ] Add `ConflictException` and `ResourceNotFoundException`.
- [ ] Write the migration script (Section 5).

**Acceptance:** `./mvnw compile` succeeds (tests/mappers may still be broken at this point; fix compile errors in tests minimally or defer to Phase 4 — but main code must compile). H2 context creates the schema without errors (`BumberoosApplicationTests` after fixing compile).

### Phase 2 — Services
- [ ] `StaffService`, `InventoryService`: new methods and rules (4.3).
- [ ] `IncidentService`: CRUD/PATCH changes and filters; remove old deploy/undeploy.
- [ ] `DispatchService`: all 8 methods with locking order (D11).
- [ ] Ensure transactions: `DispatchService` class-level `@Transactional`; creation is all-or-nothing.

**Acceptance:** service-level behaviour matches the rules tables in 4.2 and 4.3 (verified by Phase 4 tests).

### Phase 3 — DTOs, mappers, controllers, errors
- [ ] Update/add records: `IncidentRequest`, `IncidentUpdateRequest`, `IncidentResponse` (+ nested `StaffAssignmentResponse`, `ItemAssignmentResponse`, `AlarmEventResponse`), `ItemLine`, `ResolveRequest`/`ItemReturn`, `ItemRequest`/`ItemUpdateRequest`/`ItemResponse`.
- [ ] Update `IncidentMapper` (assignments, history, derived `deployedStaffIds`) and `ItemMapper` (derived availability, new fields). Mind lazy collections: mapping happens inside the open-in-view request scope today; keep it that way, or fetch associations explicitly if you hit `LazyInitializationException`.
- [ ] `IncidentController`: new endpoints (4.4), calling `DispatchService` for create/assign/release/resolve/reopen/delete and `IncidentService` for read/update. Remove the controller's own staff lookup.
- [ ] `StaffController`/`InventoryController`: new filters and fields.
- [ ] `ApiExceptionHandler`: handle `ConflictException` (409), `ResourceNotFoundException` (404), `ResponseStatusException` (`{"error": reason}`).

**Acceptance:** app starts; the example calls in Section 8 behave as described.

### Phase 4 — Tests
See Section 8 for the full list. Update existing tests broken by the model changes (removed `PENDING`, removed `deployedStaff`, new required `nature`).

**Acceptance:** `./mvnw test` is green.

### Phase 5 — Manual verification
Run the app against H2 or a scratch DB and execute the curl scenarios in Section 8.3.

### Phase 6 — Documentation
- [ ] Update `system_docs.md`: data model (3.1–3.3), API (5.4–5.6), business logic, tests section, known issues (remove fixed ones: #1, #5, #6, #7, #8, #4 if done; note new limitations).
- [ ] Delete `current_situation_1.md` (stale; known issue #10).
- [ ] Add a short note in `system_docs.md` on the migration script and its run order.

---

## 7. Backlog (do NOT implement now)

Candidate next steps, in rough priority:
1. **After Operations Report** (BFP requires one within 3 days of each response): generate from incident + assignment data, track due date and submitted flag.
2. Structured location: barangay, city, landmark, lat/lng; `alertSource` (911, text, radio, BFP referral); caller info.
3. Volunteer credentials: BFP ID number, ID district color, 18+ check from birth date, dated trainings, driver qualification (emergency-driving training, drug test, neuropsych) with expiry; replace the `employed` flag with a membership status.
4. Casualties/impact: injuries, deaths, families affected, estimated damage, responder injuries.
5. Command details: Fire Ground Commander name/station, `bfpNotifiedAt`, `reportedToFgcAt`, `releasedByFgcAt`.
6. Authentication and role enforcement (brigade chief, dispatcher, team leader); restrict open sign-up.
7. Incident type as enum (fire type, cause) for consistent statistics; non-fire categories.
8. Pagination, Flyway/Liquibase, timezone handling (`Instant`/Asia/Manila), duty roster, SMS/push alerts, remove deprecated `severityLevel`.

---

## 8. Test plan

Use the existing styles: Mockito `*MockTest` for rule logic and Spring + H2 for integration. Add `MockMvc` controller tests (check which test-slice annotations/imports are correct for Spring Boot 4.1; if the slice annotations are awkward, use `@SpringBootTest` with `@AutoConfigureMockMvc`).

### 8.1 `DispatchService` (H2 integration unless noted)
- Create incident with staff + items → incident `ONGOING`, staff `DISPATCHED`, `quantityDispatched` incremented, assignment rows active.
- Create incident where one staff member is already `DISPATCHED` → 409 and **nothing persisted** (no incident row, no other staff changed).
- Create incident with unknown staff id / item id → 404 and rollback.
- Duplicate `staffIds` and duplicate item lines are merged, not double-assigned.
- `assignStaff` on a non-`AVAILABLE` or non-employed staff → 409; idempotent when already assigned to the same incident; role updates.
- `assignStaff` on a `RESOLVED` incident → 409.
- `releaseStaff` → staff `AVAILABLE`, `releasedAt` set; second call no-op.
- `setItemQuantity`: create, increase (`delta > 0` checks availability), decrease (partial return), `delta == 0`, request exceeding available → 409, non-`GOOD` item → 409.
- `releaseItem` returns all units.
- `resolve`: all staff → `AVAILABLE`, items' `quantityDispatched` back to 0 (given no other incident holds them), `resolvedAt` set, status `RESOLVED`; applies `returnCondition` (item becomes `UNAVAILABLE` when `DAMAGED`); second `resolve` no-op; unknown item in `itemReturns` rejected.
- `resolve` with an item shared across two incidents (e.g. 10 suits, 4 on incident A and 3 on incident B) only returns that incident's units.
- `reopen` does not re-dispatch anyone; `reopen` on `ONGOING` is a no-op.
- `deleteIncident` on `ONGOING` releases staff/items first; staff are `AVAILABLE` afterwards.
- A staff member cannot be actively assigned to two incidents (second assign → 409).

### 8.2 Other services and units
- `IncidentService`: nature/alarm validation (alarm on non-emergency → 400; switching to non-emergency clears alarm); alarm change appends `IncidentAlarmEvent`; `RESOLVED` incident nature/alarm edits → 409; status filters.
- `StaffService`: manual `DISPATCHED` rejected; availability change blocked while assigned; `employed=false` blocked while `DISPATCHED`; delete blocked with assignment history or account link (409) and allowed otherwise; availability filter.
- `InventoryService`: category/serial; reduce quantity below dispatched → 409; `increase` negative amount that would go below dispatched/zero → rejected; delete blocked with assignment history; `availableOnly` filter; derived availability values (all four states).
- `MappersTest`: new fields, derived `deployedStaffIds` (active only) vs full `staff` list, derived item availability.
- Mockito unit tests for each new `DispatchService` rule where a DB is not needed.
- A concurrency test (two threads trying to dispatch the same staff member / the last unit of an item): exactly one succeeds, the other gets 409. If flaky on H2, keep it but make it deterministic or mark clearly.

### 8.3 Controller tests (`MockMvc`) and manual curl scenarios
- `POST /api/incidents` with `nature` missing → 400 `{"error": "..."}`; with staff + items → 201 and response shows active assignments.
- `PUT /api/incidents/1/items/7?quantity=2` then `...quantity=5` (sending more), `DELETE` staff, `POST /resolve`, `POST /reopen`.
- Not-found cases return `{"error": ...}` bodies (not Spring's default).
- 409 mapping for conflicts.

Example manual flow:
```bash
curl -X POST localhost:8080/api/incidents -H 'Content-Type: application/json' -d '{
  "nature":"EMERGENCY","alarmLevel":"FIRST_ALARM","incidentType":"Fire",
  "location":"Zone 3","reportedBy":"J. Cruz","staffIds":[1,2],
  "items":[{"itemId":1,"quantity":1},{"itemId":2,"quantity":4}]}'
curl -X PUT  'localhost:8080/api/incidents/1/staff/3?role=Driver'
curl -X PUT  'localhost:8080/api/incidents/1/items/2?quantity=6'
curl -X PATCH localhost:8080/api/incidents/1 -H 'Content-Type: application/json' -d '{"alarmLevel":"SECOND_ALARM"}'
curl -X POST localhost:8080/api/incidents/1/resolve -H 'Content-Type: application/json' \
  -d '{"itemReturns":[{"itemId":1,"condition":"DAMAGED"}]}'
curl localhost:8080/api/staff?availability=AVAILABLE
```

---

## 9. Defaults for open questions (use these unless the human says otherwise)

| Question | Default |
|---|---|
| Quantity-based items vs one row per unit | Quantity-based (D4). Recommend one row per unit for engines, ambulances, SCBA sets. |
| Keep or remove `severityLevel` | Keep as optional, `@Deprecated` (D8). |
| Is existing Neon data disposable | Yes; migrate old join rows as released history (Section 5, step 5). |
| Resolve/reopen on wrong-state incident | Idempotent no-op. |
| Alarm level downgrade | Allowed; every change recorded in history. |
| Return condition on bulk rows | Applies to the whole row; documented limitation. |

---

## 10. Risks and gotchas

- **Check constraints on enum columns** can silently block new enum values in Postgres even though tests (H2, `create-drop`) pass. Section 5 covers this; do not skip it.
- **Lazy loading in mappers:** responses now embed assignment lists. Keep mapping inside the request scope or fetch explicitly. Watch for N+1 queries on list endpoints (`GET /api/incidents`); a join fetch/`@EntityGraph` is fine if it becomes noticeable.
- **Locking on H2:** `PESSIMISTIC_WRITE` works with `SELECT ... FOR UPDATE` in H2's PostgreSQL mode, but confirm in tests. Keep lock order (D11) everywhere to avoid deadlocks.
- **`orphanRemoval` + release-not-delete:** because releasing sets `releasedAt` instead of removing rows, do not remove entries from the incident's assignment collections when releasing, or they will be deleted.
- **Quantity drift:** every change to `quantityDispatched` must go through `DispatchService` (assign, set quantity, release, resolve, delete incident). Do not add other code paths that touch it. Consider a defensive assertion `0 <= quantityDispatched <= quantity` after each mutation.
- **API changes that affect clients:** `status` and `severityLevel` are no longer required on incident create; `PENDING` is gone; the 404 body shape changes; `PUT/DELETE staff` return `IncidentResponse`. Mention these in `system_docs.md`.
- Do not widen scope into the backlog. If you think something in the backlog is needed to finish a phase, stop and say why in your summary.

---

## 11. Definition of done

- [ ] All three features work end to end: create-with-assignments, modify assignments, status sync both directions, emergency/non-emergency and ongoing/resolved.
- [ ] No staff member can be actively assigned to two incidents; no item can be over-dispatched; races return 409.
- [ ] Resolve/delete of an incident returns all staff to `AVAILABLE` and all item units to available (except items returned non-`GOOD`).
- [ ] `./mvnw test` is green, including the new tests in Section 8.
- [ ] Migration script written and documented, **not** run by you.
- [ ] `system_docs.md` updated; `current_situation_1.md` deleted.
- [ ] Final summary lists: what changed per phase, assumptions made (Section 9 defaults used), API-breaking changes, and anything left undone.
