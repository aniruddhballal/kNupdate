# kNupdate backend

The backend will be a small Kotlin/Ktor service backed by PostgreSQL. It is responsible for authentication, symmetric connection rules, activity visibility, push-recipient selection, and hard deletion.

The current connection and activity implementations are intentionally in-memory development slices. They use distinct development identities, short-lived hashed codes, bearer sessions, symmetric connection records, structured validation, and the two-mutual rule. When `SEED_USER_IDS` is empty in development, the code path permits bootstrap connections so the first local users can test the flow. The PostgreSQL schema is now checked in; the next persistence step is to replace this store with repositories and an explicit owner-controlled seed allowlist before production.

Planned implementation order:

1. Ktor service and environment configuration.
2. PostgreSQL migrations and repository layer.
3. Authentication and profile endpoints.
4. Connection eligibility and request endpoints.
5. Activity/feed endpoints.
6. FCM device registration and notifications.
7. Expiry deletion worker.

See [../docs/api-contract.md](../docs/api-contract.md), [../docs/privacy-and-deletion.md](../docs/privacy-and-deletion.md), and [../docs/threat-notes.md](../docs/threat-notes.md) before adding routes or tables.
