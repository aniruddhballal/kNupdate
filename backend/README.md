# kNupdate backend

The backend will be a small Kotlin/Ktor service backed by PostgreSQL. It is responsible for authentication, symmetric connection rules, activity visibility, push-recipient selection, and hard deletion.

Planned implementation order:

1. Ktor service and environment configuration.
2. PostgreSQL migrations and repository layer.
3. Authentication and profile endpoints.
4. Connection eligibility and request endpoints.
5. Activity/feed endpoints.
6. FCM device registration and notifications.
7. Expiry deletion worker.

See [../docs/api-contract.md](../docs/api-contract.md), [../docs/privacy-and-deletion.md](../docs/privacy-and-deletion.md), and [../docs/threat-notes.md](../docs/threat-notes.md) before adding routes or tables.
