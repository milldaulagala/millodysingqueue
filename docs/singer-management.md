# Singer registration and directory

## Scope

Task 1003 delivers a local admin screen to register singers and search the directory. There are no login, song, queue, edit or delete screens in this increment. Keep the application on localhost until authentication and authorisation are implemented.

Singer names are not unique. When a nickname is provided, the singer name and nickname together must be unique, ignoring case and surrounding spaces. For example, Mill / Bandara cannot be registered twice, but Mill / Host and Mick / Bandara are allowed. Nickname remains optional; multiple singers with the same name and no nickname are still permitted. PostgreSQL generates the permanent `singer_id`; email is optional and is never the primary key.

## Start locally

From the repository root, with JDK 21 selected:

```sh
colima start --cpu 2 --memory 2
# First setup only; leave an existing .env unchanged:
./scripts/setup-local-env.sh

docker compose up -d --wait postgres
./scripts/db.sh update
./scripts/run.sh
```

If `.env` already exists, skip the setup command. The run helper loads that trusted local file and supplies application URL/user defaults compatible with files generated in task 1002. It uses `APPLICATION_DB_PASSWORD`, never the migration or administrator password. No passwords are printed. Stop Ktor with Control+C; the connection pool closes during shutdown.

Open [http://localhost:8080](http://localhost:8080). To use another port, run `PORT=8081 ./scripts/run.sh`. The application does not automatically start PostgreSQL or run Liquibase. If database credentials are missing or PostgreSQL is unavailable at startup, startup fails rather than substituting in-memory storage.

For an IntelliJ Gradle `run` configuration, supply `APPLICATION_DB_URL`, `APPLICATION_DB_USER`, and `APPLICATION_DB_PASSWORD` as environment variables. Keep credentials in your local run configuration, outside Git. Running the helper in IntelliJ's terminal is also supported.

## Screen behaviour

- Warm cream-orange page (`#fff7ed`), white cards, navy text and dark teal section labels (`#007f79`).
- Singer name is required, with a 100-character limit. Nickname is optional with the same limit.
- Email is optional, with practical address-format validation and a 254-character limit. It is not mailbox verification. Email casing is preserved, and the existing database index enforces case-insensitive uniqueness.
- Leading/trailing whitespace is trimmed. Blank nickname/email becomes NULL. Control characters in names/nicknames are rejected.
- Registration shows field-level errors and moves focus to the first invalid field. Inputs are preserved on failure and disabled while saving to prevent double submission.
- A successful save clears the form, announces success, clears search and reloads the directory. If reload fails, the save remains successful and the directory error offers a refresh.
- Search filters the loaded directory by name, nickname or email, ignoring case. The registered count always refers to the full directory, with a separate filtered result count.
- Refresh loads persisted data again; it does not clear the form. On refresh failure, previously loaded rows remain visible and are marked as the last loaded directory. An initial failure shows an error rather than an empty directory.
- Requests time out after 15 seconds. A failed/timed-out registration may have reached the server: refresh the directory before resubmitting. POST requests are not retried automatically.
- User input is rendered through text widgets, not interpreted as HTML. The form supports keyboard submission, associated labels, visible focus and live status messages. Cards stack at narrow widths.

The directory currently loads all singers, suitable for the initial small local audience. Server pagination and larger-directory search can be introduced when needed.

## API contract

Both endpoints use JSON, with camelCase property names. Known singer endpoint responses use `Cache-Control: no-store`.

### POST /api/singers

Request:

```json
{"singerName":"Mick","nickName":"Work Mick","email":null}
```

Returns **201 Created**:

```json
{
    "singerId": "12",
    "singerName": "Mick",
    "nickName": "Work Mick",
    "email": null,
    "createdAt": "2026-10-31T03:00:00Z"
}
```

IDs are JSON strings to preserve PostgreSQL BIGINT precision in JavaScript. Timestamps are UTC ISO-8601 strings. Optional fields may be omitted or null in requests and are returned explicitly as null when absent. Requests must use string values for populated fields; unknown fields are rejected.

### GET /api/singers

Returns **200 OK** with an array of singer responses, or `[]` for an empty directory. Order is case-insensitive singer name, then singer ID for stable ordering of duplicate names.

### Errors

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing/invalid fields; `fieldErrors` maps property names to messages |
| 400 | `INVALID_REQUEST` | Malformed JSON, wrong property types or unknown fields |
| 409 | `NICKNAME_ALREADY_EXISTS` | Name/nickname pair already exists; includes a nickname field error |
| 409 | `EMAIL_ALREADY_EXISTS` | Email already belongs to a singer; includes an email field error |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Request is not `application/json` |
| 503 | `STORAGE_UNAVAILABLE` | Database operation failed; offer manual retry |
| 500 | `INTERNAL_ERROR` | Unexpected failure; no internal exception details are returned |

Example:

```json
{
    "code": "VALIDATION_ERROR",
    "message": "Check the highlighted fields.",
    "fieldErrors": {"singerName": "Enter the singer's name."}
}
```

`fieldErrors` may be omitted when there are no field-specific errors. `/api/health` remains a process health endpoint, not a database readiness probe. No single-singer GET endpoint is added yet.

## Implementation boundaries

- **Routes:** HTTP/JSON decoding, status codes and response mapping.
- **Service:** input normalisation and business validation.
- **Repository:** Exposed SQL DSL queries and database-error translation.
- **ApplicationDatabase:** HikariCP pool ownership and shutdown. Five maximum connections, bounded connection/statement timeouts and PostgreSQL TCP keepalive.
- **Liquibase:** schema ownership and explicit migrations; Exposed never creates or alters tables.

JDBC transactions run on `Dispatchers.IO`, with explicit database selection. Insert plus generated-value retrieval is one transaction. Database unique indexes, rather than pre-insert lookups, resolve concurrent duplicate-email and name/nickname-pair attempts. Failed inserts roll back. Database operations are not automatically retried.

The GWT API client handles JSON/HTTP separately from screen widgets. No frontend framework or JavaScript build tool has been added.

## Verification

```sh
./scripts/check.sh
```

Unit/API tests run without Docker using test-only repositories. PostgreSQL integration tests use disposable containers and the restricted application role, including pooled Exposed access, persisted reads after reopening a pool, duplicate conflicts, API-to-database behaviour, and clean pool shutdown. They do not modify the Compose development database.

Browser acceptance checks:

1. Register Mill, then two singers named Mick with different nicknames; leave their email blank.
2. Refresh the page and confirm all three remain, with distinct Mick entries.
3. Search by name, nickname and email; clear search and check the count.
4. Submit a missing name or invalid email and check field messages/focus.
5. Register an email, then try its uppercase equivalent and confirm the conflict.
6. Stop Ktor, refresh the directory, and confirm an error with previous rows retained. Restart Ktor and refresh again.
7. Check keyboard submission and the stacked layout at narrow widths.

## Updating an existing task 1003 development database

Stop the application. The new `002-unique-singer-nickname.sql` migration adds the pair constraint without changing existing singers. Resolve existing duplicate named pairs before applying it. For disposable test rows, clear them yourself if desired; the migration never deletes or renames rows. Then run `./scripts/db.sh update` and restart with `./scripts/run.sh`. No volume reset is needed.

The frontend displays `nickname already exists. please choose a different nickname` beside Nickname and focuses that field. Change the nickname and submit again. The previously applied `001-create-singers.sql` changeset remains unchanged.
