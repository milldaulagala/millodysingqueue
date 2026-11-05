# Workspace ownership

Task 1005 adds workspace ownership to singers and songs. A workspace is a customer's data boundary; it is not a login account. Authentication, memberships and request-level authorisation are later work. This version remains for localhost development only.

## Schema and upgrade

`app.workspaces` contains generated `workspace_id` (BIGINT), required `workspace_name` (maximum 200 characters, nonblank), and `created_at` (TIMESTAMPTZ). Migration `004-add-workspaces.sql` creates one initial workspace named **MillodySingQueue** and assigns all existing singers and songs to it. Their IDs, timestamps and other values are preserved. No reset is needed and previous changesets remain unchanged.

Both tables require `workspace_id`, with foreign keys preventing nonexistent ownership and restricting deletion of populated workspaces. Duplicate email and singer-name/nickname rules apply within a workspace, retaining their case/whitespace handling and optional-field behaviour. YouTube video IDs remain case-sensitive and unique within a workspace. The same values can exist in different workspaces.

The singer workspace index covers all singer listings, including rows excluded from partial unique indexes. The song unique constraint's leading workspace column covers song workspace lookups. The runtime role can read workspace records but cannot insert, update or delete them or use their identity sequence. Administrative workspace creation is not exposed through the API.

## Local setup

Stop the running application before upgrading. With Colima and the Compose PostgreSQL service running, from the repository root:

```sh
./scripts/db.sh update-sql
./scripts/db.sh update
```

Inspect the initial workspace using your existing database console:

```sql
SELECT workspace_id, workspace_name FROM app.workspaces;
```

Add its ID to your ignored `.env` file. For the initial workspace created on a fresh database this is:

```sh
APPLICATION_WORKSPACE_ID=1
```

The setup script and `.env.example` include this setting for new environments. Existing `.env` files are not overwritten; add the setting explicitly. Do not place passwords in documentation or commits.

Start the application:

```sh
./scripts/run.sh
```

Missing, malformed, nonpositive or nonexistent workspace configuration prevents startup. There is no automatic fallback to another workspace. Ktor verifies the configured workspace exists but never applies migrations.

## Backend boundary

Both Exposed repositories require a `WorkspaceContext`. Inserts explicitly set its ID; listings filter by it. Singer/song payloads and response contracts are unchanged, and the browser cannot select a workspace using a header, query parameter or form field. No workspace selector is introduced.

The configured development context selects one workspace for the entire local application. It does **not** authenticate callers. Repository filtering is application-level scoping, not PostgreSQL row-level security: the database application role can access records across workspaces through direct SQL. Do not expose this version publicly or treat it as a completed multi-customer security solution.

Before public use, authenticated membership must resolve the authorised workspace on every request. Every future lookup, update and delete must include workspace ownership. Future events and performances must enforce same-workspace references as well as ordinary foreign keys. Organiser display names must never grant access.

## Verification

```sh
./scripts/check.sh
```

The PostgreSQL integration fixture first applies migrations 001–003, inserts representative legacy singer/song records, and upgrades to the current schema. It verifies the original IDs, values, timestamps and migration execution records are preserved. A separate empty test database verifies a full fresh installation and runtime privileges. The migration preview checks DDL without applying it, and changesets are rerun/validated for idempotency.

Tests cover workspace-scoped listing and inserts, duplicates within/across workspaces, missing/nonexistent ownership, restricted workspace administration, configuration validation and client attempts to select another workspace. Existing concurrency and singer/song API tests remain in the full suite. All integration data lives in disposable containers, separate from your Compose database.

Manual acceptance checks:

1. Apply the migration and configure the initial workspace ID.
2. Open Singers and Songs; verify existing records are still present.
3. Register a singer and save a song, then refresh and search both lists.
4. Verify existing duplicate messages still appear.

No login, memberships, billing, events, performances or workspace-management UI is included in this increment.
