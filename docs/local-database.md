# Local PostgreSQL and Liquibase

## Versions and boundaries

- PostgreSQL **17.11**, official Debian Bookworm image pinned to a multi-architecture digest in Compose and the integration test.
- Liquibase **5.0.4** and PostgreSQL JDBC **42.7.14**, resolved through Gradle and locked with the other backend dependencies. No global Liquibase or PostgreSQL installation is required.
- PostgreSQL 17.11 is listed in the RDS release notes. Check availability in the chosen AWS region again before deployment.
- The Ktor application now connects through Exposed and HikariCP as `millody_app`. It never runs migrations on startup. `/api/health` checks the running process, not database readiness. See [Singer management](singer-management.md) and start locally with `./scripts/run.sh` after migrating.

References: [PostgreSQL version policy](https://www.postgresql.org/support/versioning/), [RDS release notes](https://docs.aws.amazon.com/AmazonRDS/latest/PostgreSQLReleaseNotes/postgresql-versions.html), [Liquibase PostgreSQL support](https://docs.liquibase.com/community/integration-guide-5-0-4/connect-liquibase-with-postgresql).

## First setup

Run from the repository root on macOS:

```sh
colima start --cpu 2 --memory 2
./scripts/setup-local-env.sh
docker compose up -d --wait postgres
docker compose ps
```

The setup script generates three independent random passwords in `.env` with owner-only permissions. It refuses to overwrite an existing file and does not print passwords. `.env` is ignored by Git. Alternatively, copy `.env.example` to `.env`, replace the three password placeholders, and run `chmod 600 .env`.

`.env` uses shell-compatible assignments because `scripts/db.sh` sources it to export the migration configuration. Only source your own trusted local configuration. Quote manually chosen values containing shell metacharacters. Do not put credentials in JDBC URLs, commands, screenshots, or commits.

The database is `millodysingqueue`. It is published at `127.0.0.1:5432` by default. If that port is already used, change `POSTGRES_PORT` in `.env` before starting Compose. The migration URL expands that port automatically. Stop/recreate the service after changing its port; do not delete the volume.

## Database roles

| Role | Purpose | Password setting |
| --- | --- | --- |
| `postgres` | Local administrative bootstrap and backup/restore | `POSTGRES_PASSWORD` |
| `millody_migrator` | Owns `app` and `liquibase` schemas; applies migrations | `MIGRATION_DB_PASSWORD` |
| `millody_app` | CRUD access to application tables and identity sequence usage | `APPLICATION_DB_PASSWORD` |

The application role cannot create schemas/databases, create or alter application tables, or access Liquibase tracking tables. Neither application nor migration role is a superuser. The migration role's default privileges grant application DML permissions to new tables in `app`; Liquibase metadata is isolated in `liquibase`.

`database/init/001-roles.sql` is bootstrap SQL, executed by the official PostgreSQL image **only when the data volume is empty**. It is shared by local Compose and integration tests. These local admin operations are not the future RDS provisioning mechanism.

Changing `.env` does not rotate passwords inside an existing database, and modifying bootstrap SQL does not rerun it on an existing volume. Rotate established credentials explicitly through an administrative connection and update local configuration together; do not reset real data to change a password.

## Migration workflow

From the repository root:

```sh
./scripts/db.sh validate
./scripts/db.sh status
./scripts/db.sh update-sql
./scripts/db.sh update
./scripts/db.sh status
```

- `validate` checks changelog structure and applied checksums; it does not prove SQL is valid against PostgreSQL.
- `status` reports pending changesets.
- `update-sql` previews pending SQL without creating the application table.
- `update` applies pending changesets; running it again is safe and applies nothing new.

Liquibase may initialise its tracking/lock tables during inspection commands. These commands are not guaranteed to be read-only against the tracking schema. SQL preview is for inspection, not a script to execute manually outside Liquibase.

The equivalent Gradle tasks, run from `backend/`, are `dbValidate`, `dbStatus`, `dbUpdateSql`, and `dbUpdate`. Supply `MIGRATION_DB_URL`, `MIGRATION_DB_USER`, and `MIGRATION_DB_PASSWORD` in the environment. They are separate commands; `run`, `test`, and application startup do not apply migrations to the development database.

The initial changelog creates `app.singers`:

| Column | Constraint |
| --- | --- |
| `singer_id` | Generated-always BIGINT identity primary key |
| `singer_name` | Required, contains a non-whitespace character; duplicate names permitted |
| `nick_name` | Optional |
| `email` | Optional; no whitespace or empty value; unique ignoring case |
| `created_at` | Required timestamp with time zone, default current timestamp |

The singer-management migration also enforces a case-insensitive unique singer-name/nickname pair when a nickname is provided. Mill / Bandara cannot be repeated, while Mill / Host is allowed. Missing nicknames remain optional and do not participate in this uniqueness rule.

Email casing is preserved, with a unique index on `lower(email)`. Multiple NULL emails are allowed. The singer API trims user input before saving and applies practical email-format validation. Current constraints reject whitespace anywhere in a supplied email.

Add new migrations under `backend/src/main/resources/db/changelog/changes/` and include them in the root XML changelog. Do not edit an applied changeset or clear checksums to hide an unexpected edit. Correct mistakes with a new changeset. The first table creation has no automatic rollback because dropping it would destroy singer data; use forward fixes and tested backups for recovery.

## DataGrip

Create a PostgreSQL data source:

```text
Host:       127.0.0.1
Port:       5432 (or POSTGRES_PORT from .env)
Database:   millodysingqueue
User:       millody_app
Password:   APPLICATION_DB_PASSWORD from your local .env
Schema:     app
```

Download the PostgreSQL driver if DataGrip asks, click **Test Connection**, and select the `app` schema for introspection. Use `SELECT * FROM app.singers;` after migration. The `liquibase` schema is intentionally inaccessible to this role. For schema inspection/administration, use a separate, clearly labelled migration/admin connection. Plain local TCP is for loopback development; AWS connections will require TLS and private networking.

## Tests and CI

Run from the repository root:

```sh
./scripts/check.sh
```

This runs the integrated clean build, the existing Ktor tests, and database integration tests. To run only the database tests:

```sh
./scripts/check.sh integrationTest
```

The helper follows the active Docker context, configures Testcontainers for the standard Colima Unix socket and localhost port forwarding, and preserves explicit overrides. It also works with the Docker engine on GitHub's Ubuntu runner. For other runtimes/remote hosts, configure Testcontainers explicitly; see its [runtime guidance](https://java.testcontainers.org/supported_docker_environment/).

The integration suite starts a separate PostgreSQL container with random test credentials and a random published port, uses the same bootstrap and changelog, and removes the container afterward. It does not read `.env` or use the Compose development database. Docker is required; tests fail rather than silently skip when the runtime is unavailable. Testcontainers' cleanup helper (Ryuk) remains enabled.

The suite verifies fresh migration and SQL preview, safe reruns, generated IDs/timestamps, nicknames, duplicate names, case-insensitive email uniqueness, NULL emails, invalid input rejection, application CRUD, and denied DDL/metadata access. Reports are under `backend/build/reports/tests/integrationTest/`. Ktor tests are under `backend/build/reports/tests/test/`.

For only the existing application tests, `cd backend && ./gradlew test` still works without Docker. To refresh dependency locks after intentional dependency changes, run `./scripts/check.sh build --write-locks` and review the resulting lockfile.

## Stop and restart without losing data

```sh
docker compose stop postgres
docker compose up -d --wait postgres
```

`docker compose down` also preserves the named volume; the next `up` recreates containers and reuses the data. `colima stop` preserves its virtual disk. Treat the local volume as development storage, not as a backup.

## Backup and restore

From the repository root, create a custom-format backup using the container's client:

```sh
mkdir -p backups
chmod 700 backups
docker compose exec -T postgres pg_dump -U postgres -d millodysingqueue -Fc > backups/millodysingqueue.dump
chmod 600 backups/millodysingqueue.dump
```

Keep backups private and outside Git; choose a new filename if retaining an earlier backup. This dump contains database objects/data, not login roles or their passwords. A restore target must have the bootstrap roles/schemas provisioned. Verify `pg_dump` succeeds before treating a redirected file as a usable backup.

Restore to a **separate, empty scratch database**, not over the working development database:

```sh
docker compose exec -T postgres createdb -U postgres millodysingqueue_restore
docker compose exec -T postgres pg_restore -U postgres -d millodysingqueue_restore --exit-on-error < backups/millodysingqueue.dump
docker compose exec -T postgres psql -U postgres -d millodysingqueue_restore -c 'SELECT count(*) FROM app.singers;'
```

The local server already has the required cluster-level roles; a newly provisioned server needs them created first. Review the restored rows and Liquibase tracking records. Drop the scratch database explicitly after inspection if it is no longer needed.

## Destructive local reset — only for disposable data

**The following command permanently deletes this Compose project's PostgreSQL volume and all data in it. Do not run it against data you want to keep.**

```sh
docker compose down --volumes
docker compose up -d --wait postgres
./scripts/db.sh update
```

An empty volume reruns bootstrap using the current `.env`. Do not use `colima delete`, broad Docker prune commands, or volume deletion as ordinary shutdown steps.
