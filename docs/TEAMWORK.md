# Teamwork & Collaboration (fill in before presenting)

Rubric: *Coding standards, readability, output correctness* — 5 marks.

## Team roles  *(replace with your real names)*

| Member | Responsibility | Main packages |
|---|---|---|
| _Name 1_ | Database & DAOs, seed data | `dao/`, `database/` |
| _Name 2_ | Recommendation engine & analytics | `engine/`, `service/AnalyticsService` |
| _Name 3_ | Servlets, filters, security | `web/`, `util/` |
| _Name 4_ | Frontend UI/UX | `frontend/` |
| _Name 5_ | Testing, documentation | `src/test`, `docs/` |

## Working agreements

- **Branches:** `main` is always runnable; features on `feature/<area>-<short-name>`; merge by pull request with one reviewer.
- **Commits:** imperative, small, scoped — e.g. `feat(engine): add recency decay to collaborative scorer`.
- **Definition of done:** compiles, `test.bat` is green, no unused code, Javadoc on new classes, UI checked at phone width.
- **Coding standard:** Java 17, 4-space indent, `UpperCamelCase` types, `lowerCamelCase` members, one top-level type per file, no business logic in servlets, no SQL outside DAOs, no JDBC types above the DAO layer.

## Review checklist

- [ ] Input validated server-side
- [ ] SQL uses `PreparedStatement`
- [ ] Multi-statement writes run in `Database.inTransaction`
- [ ] Errors are typed exceptions (no `catch (Exception) {}`)
- [ ] New behaviour has a test
