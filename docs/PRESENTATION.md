# Presentation Outline (≈ 10 minutes) — follows Rubric B order

| # | Slide | Time | Say / show |
|---|---|---|---|
| 1 | Title & problem | 0:30 | Information overload → personalised discovery |
| 2 | Requirements & actors | 1:00 | Admin vs User, 4 functionalities (DESIGN §1) |
| 3 | Architecture + ER diagram | 1:30 | Layered Java stack; 7 tables (DESIGN §2, §4) |
| 4 | **Core Java** | 1:30 | Strategy pattern, `PriorityQueue` top-K, parallel `CompletableFuture`, exception hierarchy |
| 5 | **JDBC & transactions** | 1:00 | Show `Database.inTransaction` + rollback test |
| 6 | **Servlets & sessions** | 1:00 | Filter chain, session fixation defence, role check |
| 7 | **Live demo** | 3:00 | Script below |
| 8 | Testing & quality | 0:30 | 27 JUnit tests, green run |
| 9 | Innovation | 0:30 | Explainability, suggestion engine, dark mode |
| 10 | Team & Q&A | 0:30 | Roles (TEAMWORK.md) |

## Live demo script
1. Login as **demo user** → For You → point at the *match %*, the reason line and the signal bar.
2. Like / buy / "not interested" → toast; Refresh → list changes.
3. Preferences → raise a category → save → Refresh.
4. Sign out → login as **demo admin** → Analytics → show CTR by signal → **Apply suggestion**.
5. System settings → move weights → Save → (optional) log in as user to show new ranking.
6. Users → view a profile, deactivate someone.

## Likely questions
- *Why H2?* Embedded JDBC DB so the project runs anywhere; switching to MySQL only needs the JDBC URL + driver (all SQL is standard).
- *Is it real AI?* Hybrid recommender: content-based filtering + user-based collaborative filtering (cosine similarity) + popularity, with recency decay — classic ML-based recommendation techniques.
- *How do you avoid recommending things already bought?* `RecommendationEngine.excludedItems`.
- *How are weights applied live?* `SettingsService` keeps a validated `volatile` snapshot, persisted in a transaction.

