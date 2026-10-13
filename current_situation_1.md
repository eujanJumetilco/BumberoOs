# current_situation_1

## 1. Current state of the codebase

**Stack:** Spring Boot 4.1.1, Java 17, Maven. `pom.xml` already includes `spring-boot-starter-data-jpa`, the PostgreSQL driver, Lombok and `webmvc`.
**Database connection:** `application.properties` points at a Neon PostgreSQL instance via `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` (loaded from `.env`). `ddl-auto=update` and `show-sql=true` are set. The connection is configured, but **nothing in the code uses it yet**.

### Layout (`com.groupnine.bumberoos`)
| Package | Contents |
|---|---|
| `domain/entities` | `StaffEntity`, `IncidentEntity`, `ItemEntity`: plain `Serializable` POJOs, not JPA entities |
| `domain/enums` | `StaffAvailability`, `IncidentSeverityLevel`, `IncidentStatus`, `ItemCondition`, each with `setX(int)` / `setX(String)` converters |
| `domain/exceptions` | `EmailAlreadyInUseException`, `InvalidCredentialException`, `InvalidInventoryExceptions`, `ValidationException` |
| `services` | `StaffService`, `IncidentService`, `InventoryService`, `AuthenticationService` |
| `controllers` | `StaffController`, `IncidentController`: empty stubs, `@RestController` commented out |
| `util` | `TextStorageUtil` (pipe-delimited txt I/O), `BinaryStorageUtil` (DataOutputStream counters/next-IDs), `StringUtil` (normalisation) |
| `src/test` | `StaffServiceTest`, `IncidentServiceTest`, `InventoryServiceTest`, `AuthenticationServiceTest` (about 800 lines) |

### How persistence works today
- Each service loads all records from `data/<module>_data.txt` in its **constructor** into an in-memory `ArrayList`.
- Every add/edit/remove rewrites the entire txt file via `TextStorageUtil`.
- IDs come from an in-memory `nextId` counter. `BinaryStorageUtil` (counts and next-IDs) is not wired into the services.
- Entities use int-coded setters (e.g. `setSeverityLevel(int)`) and normalise input inside setters via `StringUtil`.
- `StaffEntity.trainings` and `certifications` are `ArrayList<String>`, stored comma-joined in the txt file.
- Admin passwords are stored and compared in **plain text**.
- `AuthenticationService.signUp` is unfinished (`return null; // PLACEHOLDER`).
- `StaffController` is mis-wired (it injects a `StaffEntity` instead of `StaffService`).
- Working tree has one uncommitted change: a deleted line in `BumberoosApplication.java`.

## 2. Decisions made (from Q&A)
| Topic | Decision |
|---|---|
| Passwords | Hash with BCrypt (`spring-security-crypto` only, no full Spring Security) |
| Entities | **Clean redesign**: Lombok, JPA annotations, no int-code setters, service signatures change, tests rewritten |
| Trainings / certifications | `@ElementCollection` (child tables `staff_trainings`, `staff_certifications`) |
| Existing data | One-time seed from `data/staff_data.txt`, then remove importer and txt/binary utilities |
| Tests | Rewrite on H2 (test scope) so they never touch Neon |

## 3. Plan
1. **pom.xml**: add `spring-security-crypto` and H2 (test scope).
2. **Entities**: convert to `@Entity` with `@Id @GeneratedValue(IDENTITY)`, `@Enumerated(EnumType.STRING)`, `@ElementCollection`, Lombok getters/setters. Keep normalisation (`StringUtil`) in a `@PrePersist`/`@PreUpdate` hook or in the services.
3. **Enums**: drop the int/String converter helpers (JPA stores names).
4. **Repositories**: `StaffRepository` (with `findByEmailIgnoreCase`, `findByIsAdminTrue`), `IncidentRepository`, `ItemRepository` (with `findByNameIgnoreCase`).
5. **Services**: inject repositories, remove in-memory lists, counters and `saveAll`. Use `@Transactional`. Return `Optional` or throw instead of `null`.
6. **AuthenticationService**: BCrypt hashing, finish `signUp`.
7. **Seed**: one-time importer for `data/staff_data.txt`, then deleted.
8. **Cleanup**: delete `TextStorageUtil`, `BinaryStorageUtil` and `data/*.txt`. Fix the `StaffController` wiring if it is touched.
9. **Tests**: rewrite the four service tests on H2 (`src/test/resources/application.properties`).
10. **Verify**: `./mvnw test`.

## 4. Risks and notes
- Existing plaintext passwords in `data/staff_data.txt` must be hashed during seeding.
- `ddl-auto=update` is fine for development, but a migration tool (Flyway) is advisable before production.
- Changing the entity and service APIs will break any external callers. Only the tests use them today, since the controllers are empty.
