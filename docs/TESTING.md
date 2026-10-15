# Testing

Run: `test.bat` (or `mvn -f backend/pom.xml test`). Framework: JUnit 5. Integration tests use an in-memory H2 database through the real JDBC layer.

| Class | Type | What it proves |
|---|---|---|
| `UtilTest` | unit | password hashing is salted & verifiable; e-mail/password/tag/number validation; recommendation outcomes only improve |
| `EngineTest` | unit | each scorer's behaviour; engine excludes purchased/dismissed; diversity cap & list size; sort order; weights change ranking; cold start; settings validation; recency decay |
| `IntegrationTest` | integration | schema + seed load; login; brute-force lock; registration + duplicate rejection; **transaction rollback**; preference update + validation; recommendation logging & outcome upgrade; purchased items disappear; settings persist & reset; analytics report shape; last-admin protection |

## Manual test script (for the demo)

1. Sign in as `alice@example.com` → *For You* shows 8 cards with reasons.
2. Like one card, buy another → the bought card is gone after *Refresh*.
3. *Preferences* → set **Fitness = Love**, add tag `yoga` → save → *Refresh* → fitness items rise.
4. Register a brand-new user → cold-start banner appears with trending items.
5. Sign in as admin → *Analytics* → switch ranges; read the suggestions; click **Apply suggestion**.
6. *System settings* → drag weights, watch the blend bar → save → sign in as a user, results change.
7. *Users* → deactivate a user → that user can no longer log in. Try to delete yourself → blocked.
8. Try a wrong password 5× → account temporarily locked.
