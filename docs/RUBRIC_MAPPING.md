# Rubric B — Java Web Based Projects: Evidence Map

Total **50 marks**. Review 1 (pre-midterm) = 33, Review 2 (final) = 17. For each deliverable this file lists what to show the reviewer and where it lives in the code.

## Review 1 — Pre-Midterm Submission (33)

### 1. Problem Understanding & Solution Design — 8
*Clarity in requirement analysis, design diagrams, logical planning.*

| Show | Where |
|---|---|
| Requirement analysis: two actors, 4 functional requirements, 2 dashboards | [`README.md`](../README.md) "Features", [`DESIGN.md`](DESIGN.md) §1 |
| Use-case, architecture, ER, class, sequence and flow diagrams | [`DESIGN.md`](DESIGN.md) §2-§7 |
| Logical planning: layered design (Servlet → Service → DAO → DB), algorithm pipeline | [`DESIGN.md`](DESIGN.md) §2, §6 |
| Every brief item has a screen + endpoint | table in `DESIGN.md` §1 |

### 2. Core Java Concepts — 10
*Effective use of OOP, Collections, Exception Handling, Threads.*

| Concept | Where |
|---|---|
| **OOP** – abstraction (`Scorer` interface), polymorphism (3 scorer implementations), encapsulation (records, validating `RecommendationSettings`), inheritance (`ApiServlet` base class, `RecSysException` hierarchy), enums with behaviour (`InteractionType`, `RecOutcome`) | `engine/`, `web/ApiServlet.java`, `exception/`, `model/` |
| **Design patterns** – Strategy (`Scorer`), DAO, Chain of Responsibility (`AuthFilter`), Composition root / dependency injection (`AppContext`), Template method (`ApiServlet.service`) | see files |
| **Collections** – `HashMap`/`LinkedHashMap` (profiles, vectors), `PriorityQueue` (bounded top-K neighbours), `Set`/`LinkedHashSet` (exclusions, tags), `TreeMap`/`TreeSet` (sorted tags & categories), `ConcurrentHashMap` (login throttle), streams + `Comparator` chains | `CollaborativeScorer`, `RecommendationEngine`, `ItemService`, `UserService` |
| **Exception handling** – custom checked-to-unchecked translation (`DataAccessException`), typed HTTP exceptions, one central handler (`ApiServlet.service`), try-with-resources everywhere, transaction rollback on any failure | `exception/`, `Database.inTransaction` |
| **Threads / concurrency** – `ExecutorService` + `CompletableFuture` run the 3 scorers in parallel with timeout; `ScheduledExecutorService` background housekeeping job; immutable `UserContext` for thread safety; `volatile` settings cache; `ConcurrentHashMap` | `RecommendationEngine`, `AppContext`, `SettingsService` |

### 3. Database Integration (JDBC) — 8
*Schema design, CRUD, transaction handling.*

| Show | Where |
|---|---|
| Normalised schema: 7 tables, PK/FK, `CHECK`, `UNIQUE`, `ON DELETE CASCADE`, indexes | [`database/schema.sql`](../database/schema.sql), ER diagram in `DESIGN.md` §4 |
| CRUD via `PreparedStatement` (no SQL injection) | `dao/*.java` |
| Create: register user · Read: recommendations, analytics · Update: preferences, settings, roles · Delete: users (cascade) | `UserService`, `PreferenceService`, `SettingsService` |
| **Transactions**: registration (user + prefs), preference replace (name + categories + tags), interaction + recommendation-log update, settings batch upsert; commit/rollback in one helper | `Database.inTransaction`, test `failedTransactionRollsBackEveryStatement` |
| Connection pooling, batch inserts, aggregate `GROUP BY` analytics SQL | `Database`, `RecommendationLogDao` |

### 4. Servlets & Web Integration — 7
*Request/response, session management.*

| Show | Where |
|---|---|
| 8 servlets mapped with URL patterns, `doGet/doPost/doPut/doPatch/doDelete`, JSON request/response, proper status codes (200/201/400/401/403/404/409/429/500) | `web/*Servlet.java`, `WebServer.java` |
| **Session management**: `HttpSession` created at login (old session invalidated → session-fixation defence), 30-min timeout, HttpOnly cookie, invalidated at logout | `AuthServlet`, `WebServer` |
| **Filters**: `AuthFilter` (authentication + role-based authorisation for `/api/admin/*`), `SecurityHeadersFilter` (CSP, nosniff, frame-deny) | `web/` |
| Front-end ↔ back-end integration through a JSON API (`fetch`) | `frontend/js/api.js` |

## Review 2 — Final Submission (17)

### 5. Code Quality & Testing — 10
*Readability, modularity, debugging, exception handling.*

| Show | Where |
|---|---|
| Clear package-by-layer modularity; one responsibility per class; Javadoc on every non-trivial class | `backend/src/main/java` |
| Input validation in one place; passwords hashed with PBKDF2 + salt; constant-time compare; login throttling | `util/Validator`, `util/PasswordHasher`, `UserService` |
| **27 automated tests** (JUnit 5): unit tests for utilities and each algorithm, integration tests against a real JDBC database | [`TESTING.md`](TESTING.md) |
| Consistent error contract `{error, message}` and user-friendly UI messages | `ApiServlet`, `frontend/js/api.js` |

### 6. Teamwork & Collaboration — 5
*Coding standards, readability, output correctness.*

| Show | Where |
|---|---|
| Role split, branching and commit conventions, review checklist | [`TEAMWORK.md`](TEAMWORK.md) — **fill in names/commit history before presenting** |
| Consistent coding standards (naming, formatting, layering) and passing test-suite as the definition of "correct output" | whole repo |

### 7. Innovation / Extra Effort — 2
*Extra features beyond minimum requirements.*

- **Explainable recommendations** — every card says *why* and shows the signal mix.
- **Self-tuning suggestions** — analytics produce actionable advice the admin applies with one click.
- Hybrid algorithm with recency decay, diversity cap, cold-start handling.
- Admin safeguards (last-admin protection), login throttling, security headers.
- Dark mode, responsive/mobile layout, keyboard-accessible UI, offline SVG charts.
- Realistic seeded demo data so dashboards are meaningful on first launch.

