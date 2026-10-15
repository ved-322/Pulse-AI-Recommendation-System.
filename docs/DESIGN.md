# Design Document

Diagrams use [Mermaid](https://mermaid.js.org) — they render on GitHub, in VS Code (Markdown Preview Mermaid Support) and at mermaid.live (export as PNG/SVG for slides).

## 1. Requirement analysis

**Actors:** Admin, User.

| # | Requirement from brief | Screen | Endpoint | Java classes |
|---|---|---|---|---|
| A1 | System settings management | Admin → System settings | `GET/PUT/DELETE /api/admin/settings` | `AdminSettingsServlet`, `SettingsService`, `RecommendationSettings`, `SettingsDao` |
| A2 | Recommendation effectiveness analysis | Admin → Analytics | `GET /api/admin/analytics` | `AdminAnalyticsServlet`, `AnalyticsService`, `RecommendationLogDao` |
| A3 | Manage user profiles | Admin → Users | `/api/admin/users` | `AdminUserServlet`, `UserService`, `UserDao` |
| U1 | Personalised recommendations | User → For You | `GET /api/recommendations` | `RecommendationServlet`, `RecommendationService`, `RecommendationEngine` |
| U2 | Preference management | User → Preferences | `GET/PUT /api/preferences` | `PreferenceServlet`, `PreferenceService`, `PreferenceDao` |
| U3 | Behaviour capture (feeds U1) | cards: like/buy/dismiss | `POST /api/interactions` | `InteractionServlet`, `InteractionService` |

Non-functional: secure sessions, validated input, atomic updates, responsive UI, explainable output, runs with zero external installs besides JDK + Maven.

## 2. Architecture

```mermaid
flowchart LR
  subgraph Browser
    UI["HTML / CSS / JS SPA"]
  end
  subgraph "Java server (embedded Jetty)"
    F1["SecurityHeadersFilter"] --> F2["AuthFilter<br/>session + role"]
    F2 --> S["Servlets<br/>(ApiServlet base)"]
    S --> SV["Services<br/>business logic"]
    SV --> E["RecommendationEngine<br/>3 Scorers in parallel"]
    SV --> D["DAOs<br/>PreparedStatement"]
    E --> D
  end
  DB[("H2 database<br/>via JDBC pool")]
  UI -- "JSON over HTTP + session cookie" --> F1
  D --> DB
```

## 3. Use cases

```mermaid
flowchart TB
  admin((Admin)):::a
  user((User)):::u
  admin --> A1[Configure algorithm settings]
  admin --> A2[Analyse recommendation effectiveness]
  admin --> A3[Manage user profiles & roles]
  admin --> A4[Apply tuning suggestion]
  user --> U1[View personalised recommendations]
  user --> U2[Manage preferences]
  user --> U3[Like / buy / dismiss items]
  user --> U4[Explore catalogue]
  U3 -. "feeds" .-> U1
  A4 -. "extends" .-> A1
  classDef a fill:#ede9fe,stroke:#7c3aed
  classDef u fill:#dcfce7,stroke:#16a34a
```

## 4. Database (ER diagram)

```mermaid
erDiagram
  USERS ||--o{ USER_PREFERENCES : has
  USERS ||--o{ USER_TAGS : has
  USERS ||--o{ INTERACTIONS : performs
  ITEMS ||--o{ INTERACTIONS : receives
  USERS ||--o{ RECOMMENDATION_LOG : "is shown"
  ITEMS ||--o{ RECOMMENDATION_LOG : "is recommended in"
  USERS { bigint id PK
          varchar email UK
          varchar password_hash
          varchar role "ADMIN|USER"
          boolean active }
  ITEMS { bigint id PK
          varchar name
          varchar category
          decimal price
          varchar tags }
  USER_PREFERENCES { bigint user_id PK,FK
                     varchar category PK
                     int weight "0-5" }
  USER_TAGS { bigint user_id PK,FK
              varchar tag PK }
  INTERACTIONS { bigint id PK
                 bigint user_id FK
                 bigint item_id FK
                 varchar type "VIEW|CLICK|LIKE|PURCHASE|DISMISS"
                 timestamp created_at }
  RECOMMENDATION_LOG { bigint id PK
                       bigint user_id FK
                       bigint item_id FK
                       double score
                       varchar source "content|collaborative|popularity"
                       varchar outcome "SHOWN|CLICK|LIKE|PURCHASE|DISMISSED" }
  SETTINGS { varchar setting_key PK
             varchar setting_value }
```

## 5. Class diagram (core)

```mermaid
classDiagram
  class Scorer { <<interface>> +name() String +score(ctx, settings) Map }
  class ContentBasedScorer
  class CollaborativeScorer
  class PopularityScorer
  Scorer <|.. ContentBasedScorer
  Scorer <|.. CollaborativeScorer
  Scorer <|.. PopularityScorer
  class RecommendationEngine { -ExecutorService pool +recommend(ctx, settings) List~Recommendation~ }
  RecommendationEngine o-- "3" Scorer
  class RecommendationService { +recommendFor(User) Result }
  RecommendationService --> RecommendationEngine
  RecommendationService --> SettingsService
  class ApiServlet { <<abstract>> #service() #body() #ok() }
  ApiServlet <|-- AuthServlet
  ApiServlet <|-- RecommendationServlet
  ApiServlet <|-- PreferenceServlet
  ApiServlet <|-- InteractionServlet
  ApiServlet <|-- CatalogServlet
  ApiServlet <|-- AdminSettingsServlet
  ApiServlet <|-- AdminAnalyticsServlet
  ApiServlet <|-- AdminUserServlet
  class RecSysException { status code }
  RecSysException <|-- ValidationException
  RecSysException <|-- AuthenticationException
  RecSysException <|-- ForbiddenException
  RecSysException <|-- NotFoundException
  RecSysException <|-- ConflictException
  RecSysException <|-- DataAccessException
  class Database { +read(fn) +inTransaction(fn) }
```

## 6. Recommendation pipeline

```mermaid
flowchart TD
  A["GET /api/recommendations"] --> B["Load user prefs, history, all interactions"]
  B --> C["Immutable UserContext"]
  C --> D1["ContentBasedScorer"]
  C --> D2["CollaborativeScorer"]
  C --> D3["PopularityScorer"]
  D1 & D2 & D3 --> E["Blend with admin weights (normalised)"]
  E --> F["Remove purchased / dismissed"]
  F --> G["Drop score < minScore"]
  G --> H["Sort + diversity cap per category + limit"]
  H --> I["Explain each pick"]
  I --> J["Log impressions (TX)"]
  J --> K["JSON response"]
```

## 7. Sequence — user likes a recommended item

```mermaid
sequenceDiagram
  actor U as User
  participant UI as Browser
  participant F as AuthFilter
  participant S as InteractionServlet
  participant SV as InteractionService
  participant DB as Database
  U->>UI: click heart
  UI->>F: POST /api/interactions {itemId, LIKE} + cookie
  F->>F: validate session, load user
  F->>S: chain.doFilter
  S->>SV: record(userId, itemId, LIKE)
  SV->>DB: BEGIN
  SV->>DB: INSERT interaction
  SV->>DB: UPDATE latest recommendation_log outcome=LIKE
  SV->>DB: COMMIT (ROLLBACK on error)
  S-->>UI: 201 {ok:true}
  UI-->>U: toast + heart filled
```

## 8. Security design

- PBKDF2-HMAC-SHA256, 120 000 iterations, per-user salt; constant-time comparison.
- Session-fixation defence (new session on login), HttpOnly cookie, 30-minute idle timeout.
- Role check for `/api/admin/*` in a single filter; deactivated users are rejected on every request.
- All SQL via `PreparedStatement`; GROUP BY expressions come from a whitelist enum.
- UI never uses `innerHTML` with data (XSS-safe by construction); CSP restricts scripts to same-origin.
- Login throttling: 5 failures → 5-minute lock per e-mail; identical error for unknown user / wrong password.
