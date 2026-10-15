# BumberoOS – Change Log

Everything added after the baseline (backend `majorRevisoin` at `ee8568e`, frontend `trial` at `770f779`).
Both repos now sit on a branch named `feature/phase5-inventory-rules`, which contains all of the work below.
Nothing has been pushed or merged.

For how the whole system works, see `BumberoOS_System_Overview.md`. For the original plan, see `BumberoOS_Implementation_Plan.md`.
Both live in the project root, next to the two repos.

## At a glance

| # | Change | Backend | Frontend |
|---|---|---|---|
| 1 | Settings page and display preferences | – | `12dbf2e` |
| – | Timestamps stored in UTC, shown in Philippine time | `f250371` | `12dbf2e` |
| 2 | Staff ID, arrival time, BFP commander and orders | `58a2f77` | `4d5f9d3` |
| – | Research: does arrival/commander fit PH volunteers? | `28c29f6` | – |
| 3 | Incident map on the dashboard | `7354bb5` | `d5b76d5` |
| 4 | Cookie-session login, roles, user management | `5f7a539`, `4b8f8b2` | `48e1418` |
| 5 | Inventory rules, expiry dates, equipment alerts | `bfaf3f1` | `7652248` |

Backend tests grew from 177 to 238, all passing. The frontend has no test suite. It was checked with `tsc`, `eslint` and `next build`.

---

## 1. Settings page and display preferences (frontend only)

The `/settings` page was an empty heading. It now has four tabs.

- **Appearance:** theme (light, dark or follow the system), density (comfortable or compact), text size (four steps), high contrast.
- **Preferences:** landing page after login (Dashboard or Incidents), default Incidents filter (All, Ongoing or Resolved), 12-hour or 24-hour time.
- **Inventory Rules** and **User Management:** see changes 5 and 4.

How it works:
- Settings are saved in the browser's `localStorage`, per browser, not per user (`lib/settings.ts`). A "Reset to defaults" button clears them.
- A small script in `<head>` applies the theme before the first paint, so there is no flash. `SettingsApplier` keeps it in sync afterwards, including when the OS theme flips under "System".
- **Dark mode does not touch the components.** `globals.css` remaps the Tailwind gray scale and the white, red, green and amber surface classes under `[data-theme="dark"]`. Density works by changing Tailwind's `--spacing`. Text size changes the root font size.
- The login redirect uses the landing page setting. The Incidents page starts on the default filter.

## UTC storage and Philippine time (cross-cutting)

- **Backend:** the JVM is pinned to UTC in `BumberoosApplication`, and Hibernate's JDBC zone is set to UTC. A test checks it. Every stored `LocalDateTime` is now UTC.
- **Frontend:** timestamps from the server are read as UTC and always displayed in `Asia/Manila`. There is no timezone setting. "Mark under control" and "Fire out" send UTC. Date-only fields (such as last maintained) are shown as-is, so no zone can shift the day.
- **Existing data:** `docs/migrations/002_utc_timestamps.sql` subtracts 8 hours from older rows. It assumes the old server ran in Philippine time. **Run it once, by hand, before starting the new build, and back up first.** Skip it if the old data is disposable.

## 2. Staff ID, arrival time, commander and orders

**Staff ID**
- `StaffEntity.badgeNumber`: exactly 7 digits (for example `2516528`), unique, optional.
- A malformed value returns 400 and a duplicate returns 409. Saving a blank value clears it.
- It shows in the staff list, search, detail panel, personnel file and form, and on the incident's staff lists, assign dropdown and report modal.

**Arrival and commander**
- New incident fields `arrivedAt` and `commanderName`.
- New `IncidentCommanderOrder` log (`incident_commander_orders`): each instruction the BFP commander gave, with a timestamp. Add one with `POST /api/incidents/{id}/orders` (ongoing incidents only, 201).
- A "Scene & Command" panel on the Incidents page: **Mark Arrived Now**, a commander field, and the orders list. Arrival and each order appear in the dispatch feed.
- Limitation: arrival records "now". You cannot enter a past time yet.

**Research** (`docs/Research_Arrival_and_Commander.md`): this feature fits how volunteer brigades work. Fire Code rules reportedly place them under the BFP fire ground commander at the scene, and arrival time is standard fire-report data. Confidence is moderate, because the official BFP manual could not be read and several sources were secondary. The research also notes that the BFP's own term is "Fire Ground Commander". The UI still says "Incident Commander", and relabelling it was suggested but not done.

## 3. Incident map

**Backend:** optional `latitude` and `longitude` on incidents, in the create and update requests and in the response. Both must be given together, with ranges checked (−90 to 90, −180 to 180). Otherwise 400.

**Frontend:** Leaflet 1.9.4 and react-leaflet 5.0.0 on OpenStreetMap tiles, loaded only in the browser.
- **Dashboard:** an "Incident Map" card shows a red circle (about 200 m) and a dot for each ongoing incident. A "Show resolved" toggle adds resolved ones in gray. Clicking a circle opens a side panel for that incident only: type, alarm level, reporter, arrival, commander, deployed staff (name, ID, role) and equipment. Ongoing incidents without a pin are listed under the map and can still be selected.
- **Setting a pin:** a map picker in the Report Incident modal, and "Set pin" or "Move pin" on the Incidents page. You click the map or press **Find** to search an address (OpenStreetMap's Nominatim, only on button press).
- **Dark mode:** map tiles are darkened.
- **Configuration:** the default view is Manila. Set `NEXT_PUBLIC_MAP_CENTER="lat,lng"` for your town. Tiles need internet.
- Limitation: a saved pin can be moved but not cleared.

## 4. Login, roles and user management

**Backend**
- Spring Security with **cookie sessions**: `HttpOnly`, `SameSite=Lax`, 8-hour timeout. Set `SESSION_COOKIE_SECURE=true` when served over HTTPS.
- `/api/**` now requires sign-in. `/api/accounts/**` is ADMIN-only. Responses are 401 `{"error":"please sign in"}` and 403 `{"error":"administrator access required"}`.
- **Signup** is open only until the first enabled admin exists. That first account is always forced to ADMIN and signed in. After that only an admin can add accounts.
- **Effective immediately:** a filter re-reads the account on every request. Disabling or deleting an account ends its session on its next request. A role change applies to its live session.
- **Safety rules:** an admin cannot disable, demote or delete themselves, and the last enabled admin cannot be disabled, demoted or deleted. An account can be linked to only one staff member (409 otherwise).
- New endpoints: `GET /api/auth/me`, `POST /api/auth/signout`, `PATCH /api/accounts/{id}/role?value=`.

**Frontend**
- **Settings → User Management** (admins only): account table with a role dropdown, enable/disable, linked staff, delete, and an "Add account" dialog. Your own row's controls are disabled.
- The app re-checks the cached account with `/auth/me`. Any 401 returns to login. Log out ends the server session.

**Decisions to review**
- **CSRF protection is off**, relying on the `HttpOnly` + `SameSite=Lax` cookie and the same-origin `/api` proxy (documented in `SecurityConfig`).
- Sessions live in server memory, so a restart signs everyone out.
- **Not built:** login rate limiting, password change or reset.
- Dispatchers can still delete staff, items and incidents. Roles are enforced on the server only for accounts and inventory-rule changes.
- **Everyone must sign in again** after this is deployed.
- **Log message:** the line "Using generated security password" at startup is Spring Boot's default throwaway user. It is unused, because form login and HTTP Basic are turned off. It can be silenced by excluding `UserDetailsServiceAutoConfiguration`. This was not done.

## 5. Inventory rules, expiry and alerts

**Backend**
- `ItemEntity.expiryDate`.
- Rules shared by everyone, stored on the server: station-wide maintenance interval (default 180 days, 0 = off) and expiry warning window (default 30 days), plus a per-category low-stock threshold (0 = off) and maintenance interval (empty = use the default, 0 = no reminders for that category).
- `GET /api/settings/inventory-rules` for any signed-in user. `PUT` is admin-only.
- `GET /api/items/alerts` computes, on every call:
  - `EXPIRED` and `EXPIRING_SOON`
  - `MAINTENANCE_DUE`: last maintenance is older than the interval, or there is no maintenance record
  - `LOW_STOCK`: usable units in a category (good condition, not out on an incident) are below its threshold
  - `VEHICLE_DAMAGED`: damaged engines and ambulances

  Retired items are ignored. HIGH alerts sort first. "Today" is the Philippine date.

**Frontend**
- **Settings → Inventory Rules:** admins edit, everyone else sees it read-only.
- **Inventory page:** expiry and last-maintained fields on the form, a "Maintained today" button, an alert panel, per-row alert badges, a "Needs attention" filter, and the two dates in the table and CSV export.
- **Dashboard:** an "Equipment Alerts" card.
- The "Last maintained" field and "Maintained today" button are additions, because reminders depend on that date and the form had no way to set it.
- Limitation: an expiry date can be changed but not cleared once set.

---

## API changes

| Endpoint | Change |
|---|---|
| `GET /api/auth/me`, `POST /api/auth/signout` | new |
| `POST /api/auth/signup` | open only for the first admin, then admin-only |
| `PATCH /api/accounts/{id}/role` | new, admin-only |
| `PUT /api/accounts/{id}/staff/{staffId}` | now 409 if that staff member already has an account |
| `POST /api/incidents/{id}/orders` | new |
| `PATCH /api/incidents/{id}` | accepts `arrivedAt`, `commanderName`, `latitude`, `longitude` |
| `POST /api/incidents` | accepts `latitude`, `longitude` |
| `POST /api/staff`, `PATCH /api/staff/{id}` | accept `badgeNumber` |
| `POST /api/items`, `PATCH /api/items/{id}` | accept `expiryDate` |
| `GET /api/items/alerts` | new |
| `GET`, `PUT /api/settings/inventory-rules` | new (`PUT` admin-only) |

Response fields added: `badgeNumber` (staff, staff assignments), `arrivedAt`, `commanderName`, `commanderOrders`, `latitude`, `longitude` (incidents), `expiryDate` (items).

## Database changes

Hibernate's `ddl-auto=update` creates these on first start. All new columns are nullable.

- New tables: `incident_commander_orders`, `inventory_settings`, `inventory_rules`.
- New columns: `staff.badge_number` (unique), `incidents.arrived_at`, `commander_name`, `latitude`, `longitude`, `items.expiry_date`.
- One manual step: `docs/migrations/002_utc_timestamps.sql` (see above).

## Dependencies and configuration

- **Backend:** `spring-boot-starter-security` and, for tests, `spring-boot-starter-security-test` (plain `spring-security-test` did not work with Boot 4's MockMvc). New settings in `application.properties`: the UTC JDBC zone and the session cookie properties.
- **Frontend:** `leaflet` 1.9.4, `react-leaflet` 5.0.0 and `@types/leaflet` 1.9.21, pinned exactly. `npm audit` reports 5 high-severity issues; none are in these packages and they were not investigated.
- **Environment:** `NEXT_PUBLIC_MAP_CENTER` (optional), `SESSION_COOKIE_SECURE` (set to `true` over HTTPS), and the existing `API_URL`.

## Testing and what has not been verified

- **Backend:** 238 tests pass. The new ones cover staff ID rules, arrival, commander and orders, map coordinates, the security flow (sessions, roles, signup, account safety rules, instant session end), inventory rules and every alert type.
- **Real HTTP:** the cookie session was exercised with `curl` against an in-memory server, including through the Next.js `/api` proxy, and the new endpoints were smoke-tested.
- **Not verified:** none of the UI has been seen in a browser (headless Firefox hung in the development environment). Click through the dashboard map, the report-modal and Incidents pin pickers, Settings (all four tabs), the Inventory page and the dashboard alert cards.
