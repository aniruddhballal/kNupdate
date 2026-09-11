# Local infrastructure

The local development database is PostgreSQL.

Start it:

```sh
docker compose -f infra/docker-compose.yml up -d postgres
```

The local connection string is:

```text
postgresql://knupdate:change-me-local-only@localhost:55432/knupdate
```

For the backend's `DATABASE_URL`, use the JDBC form from `infra/.env.example`.

The schema migration is at `backend/src/main/resources/db/migration/V1__create_core_tables.sql`. Apply it with the migration runner once the backend persistence layer is connected. Do not use the local password or volume configuration in production.

Stop the database without deleting its local volume:

```sh
docker compose -f infra/docker-compose.yml down
```

The activity data stored by this local volume is disposable development data. Production infrastructure must be configured so database backups and retained operational stores do not violate the hard-deletion promise.
