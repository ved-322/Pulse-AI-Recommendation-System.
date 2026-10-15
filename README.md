# Pulse — AI-Based Recommendation System

A full-stack **Java web application** that suggests products and content based on each user's preferences and behaviour,
with an admin console to tune the algorithm and measure how well it works.

> **Backend: 100% Java** (Servlets + JDBC + embedded Jetty + H2). The frontend is plain HTML/CSS/JavaScript that talks to the Java servlets over a JSON API.

## Project layout

```
AI-Recommendation-System/        <- the main folder (submit / present this)
├── backend/                     <- ALL Java code (Maven project)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/recsys/
│       │   ├── Main.java                 entry point
│       │   ├── config/                   Database (JDBC pool + transactions), AppContext (wiring), seeders
│       │   ├── model/                    User, Item, Interaction, Recommendation, enums (records)
│       │   ├── exception/                custom exception hierarchy
│       │   ├── dao/                      JDBC data-access objects (CRUD)
│       │   ├── service/                  business logic (users, preferences, analytics, settings…)
│       │   ├── engine/                   recommendation algorithms (Strategy pattern + threads)
│       │   ├── web/                      Servlets, Filters, embedded Jetty bootstrap
│       │   └── util/                     password hashing, validation, JSON
│       └── test/java/com/recsys/         JUnit 5 tests
├── frontend/                    <- UI (HTML/CSS/JS, served by the Java server)
│   ├── index.html
│   ├── css/styles.css
│   └── js/ (main.js, api.js, ui.js, charts.js, views/…)
├── database/                    <- schema.sql + seed.sql (loaded by JDBC at start-up)
├── docs/                        <- design diagrams, rubric mapping, testing, presentation notes
├── run.bat / run.sh             <- build + start
└── test.bat                     <- run the tests
```

## Run it

Requirements: **JDK 17+** and **Apache Maven 3.9+**.

```bash
run.bat            # Windows   (or ./run.sh on macOS/Linux)
```

Open **http://localhost:8080**. The first start creates the database in `data/` and loads demo data.

| Role  | E-mail              | Password   |
|-------|---------------------|------------|
| Admin | `admin@recsys.com`  | `Admin@123`|
| User  | `alice@example.com` | `User@123` |

Run the tests with `test.bat` (or `mvn -f backend/pom.xml test`).

## Catalogue, photos & reviews

- **~251 real products in 14 categories** are defined in `database/catalogue/*.tsv` (one file per category; brand, name, price in INR, tags, description) and loaded through JDBC at first start.
- **Pictures** (251 of the 251 items) live in `frontend/img/<slug>.jpg`, listed in `img/index.json`: 45 books (Open Library), 44 film posters and 162 products (Wikipedia, Wikimedia Commons, Open Food/Beauty Facts). Sources and licences: [`docs/IMAGE_CREDITS.md`](docs/IMAGE_CREDITS.md). Re-run `scripts/fetch-pictures.ps1` and `scripts/fetch-pictures2.ps1` to refresh. Items without a free picture show an illustrated tile.
- **Reviews**: users write star reviews in-app; seeded demo reviews are labelled *Sample review*. A 4-5 star review also teaches the recommender. Public ratings (TMDB / Open Library) appear on items that have them.
- **Discover** is the single main screen: set interests at the top and the AI feed below updates live.

## Features (mapped to the project brief)

**Admin**
- *System settings management* — algorithm weights, minimum score, list size, diversity cap, recency half-life; validated server-side, saved transactionally, applied immediately.
- *Recommendation effectiveness analysis* — KPIs (CTR, like rate, conversion, dismiss rate), trend chart, per-algorithm and per-category performance, top items, and **rule-based tuning suggestions with one-click apply**.
- *User profile management* — list/search, view profile & interests, change role, activate/deactivate, delete (with safeguards for the last admin).

**User**
- *Personalised recommendations* — hybrid engine (content-based + collaborative + popularity), each card explains **why** it was recommended.
- *Preference management* — category interest levels, favourite tags, display name; saved atomically.
- *Behaviour tracking* — details / like / buy / "not interested" feed back into future results.

## How the recommender works

`score(item) = wC·content + wU·collaborative + wP·popularity` (weights normalised, set by the admin)

1. **Content-based** – builds a feature profile (category + tags) from explicit preferences and recency-decayed behaviour.
2. **Collaborative** – cosine similarity between users' interest vectors; the 10 nearest neighbours (bounded `PriorityQueue`) vote.
3. **Popularity** – recency-weighted engagement across all users (also solves the cold-start problem).
4. The three scorers run **in parallel** (`CompletableFuture` on a thread pool); results are filtered (bought / dismissed), diversified per category, explained and logged for analytics.

## API summary

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` `/login` `/logout` | public | account + session |
| GET | `/api/auth/me` | public | current session |
| GET | `/api/recommendations` | user | personalised list |
| POST | `/api/interactions` | user | record view/click/like/purchase/dismiss |
| GET / PUT | `/api/preferences` | user | read / update preferences |
| GET | `/api/items?category=&q=` | user | browse catalogue |
| GET / PUT / DELETE | `/api/admin/settings` | admin | read / update / reset algorithm settings |
| GET | `/api/admin/analytics?days=` | admin | effectiveness report |
| GET / PATCH / DELETE | `/api/admin/users[/{id}]` | admin | manage users |

See [`docs/`](docs) for the design (UML/ER diagrams), the **Rubric B** mapping, test plan and presentation script.
