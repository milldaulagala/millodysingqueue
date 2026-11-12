# Events and performance management

Task 1006 adds the Performances page. It prepares a saved singing order using existing singers and songs. Playback, audience screens, completed/skipped states, history views, event editing/deletion and login are later increments.

## Local upgrade

Stop Ktor, start Colima/PostgreSQL if needed, then from the repository root:

```sh
docker compose up -d --wait postgres
./scripts/db.sh update-sql
./scripts/db.sh update
./scripts/run.sh
```

Keep the existing `APPLICATION_WORKSPACE_ID` setting in `.env`. Migration 005 adds events and performances and composite keys on singers/songs. It preserves existing records and previous changesets. No reset is needed. Only Liquibase applies migrations; Ktor uses the restricted runtime role.

Open [Performances](http://localhost:8080/#performances). It is also the default page for an empty or unknown fragment. Navigation is **Performances → Singers → Songs**. The existing Singer and Song pages remain available at `#singers` and `#songs`.

## Workflow

1. Select **Create night**, enter a name, calendar date and organiser's name, then submit.
2. The new night becomes selected. On a fresh visit the latest event by date is selected; ties use newest event ID. Existing selection is preserved while switching pages. There is no automatic date rollover or event closure.
3. Select a singer (nickname shown where available), search the saved song library, and select a song.
4. Add a performance. It joins the end of the waiting order.
5. Use Up/Down to swap with an adjacent waiting performance.
6. Use the navy bin icon to remove a performance, confirming the singer/song first. This marks it `CANCELLED`, preserving its record. It is not a completed song.
7. Refresh/restart to reload the saved order. **First in line** shows the first waiting performance, not a playing video.

The same singer/song combination may be added more than once. No fairness algorithm rearranges the admin's choices. Cancelled positions can leave gaps; visible numbering is consecutive. Appending never reuses a cancelled position. The desktop order panel scrolls; small screens use normal page scrolling. There is no five-item or arbitrary 200-item product limit. Tests exercise 200 entries; this is a verification workload, not a capacity guarantee.

The create form requires name/organiser (trimmed, nonblank, at most 200 characters, no control characters) and a valid ISO date from year 0001–9999. The date is a PostgreSQL DATE, not a timestamp or timezone conversion. Multiple events on the same date are allowed. Organiser name is display information and grants no access.

If no singers or songs exist, use the Singers/Songs navigation to register/save them and return. Refresh reloads selector data and the order. Failed saves preserve input. Mutations disable competing controls, and POSTs are never automatically retried. After an unconfirmed or rejected order change, refresh before trying again: the server may already have committed it.

## Data and ownership

`events`: generated BIGINT `event_id`, `workspace_id`, `event_name`, `event_date`, `organiser_name`, `queue_version` (initially 0), `created_at` (TIMESTAMPTZ).

`performances`: generated BIGINT `performance_id`, `workspace_id`, `event_id`, `singer_id`, `song_id`, positive BIGINT `queue_position`, `status` (`WAITING` or `CANCELLED`), `created_at` (TIMESTAMPTZ).

Composite foreign keys enforce that each event, singer and song belongs to the performance's workspace. Referenced records cannot be deleted while performances depend on them. A deferred unique constraint on workspace/event/position permits atomic swaps while ensuring committed positions are unique. Indexes support workspace event selection, waiting-order reads and foreign-key lookups.

The GWT `performances` variable is `List<PerformanceData>` backed by `ArrayList`. Backend domain objects are `Event`, `Performance` and `PerformanceQueue`; HTTP response DTOs are separate. PostgreSQL is the durable source of order, and actions identify a performance by ID rather than its display index.

Every queue read/mutation locks the workspace-owned event row inside one transaction. This keeps the returned version and rows consistent and serialises additions, movement and cancellation for that event. Mutations require the last observed version; accepted changes increment it once. A stale request returns 409 without applying changes. Boundary moves return 400 without changing order/version. Missing, cancelled or unrelated performances return 404. A version is not an authentication credential.

All repositories require server-selected workspace context. Clients cannot choose ownership. Authentication and request-level membership authorisation remain required before public deployment; repository scoping is not PostgreSQL row-level security.

## JSON API

IDs, queue positions, queue versions and creation timestamps are JSON strings. Dates use `YYYY-MM-DD`. All requests with a body require `Content-Type: application/json`. Unknown fields, numeric IDs/versions, wrong types and malformed JSON are rejected. Missing/null required fields produce validation errors. Responses use `Cache-Control: no-store`.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/events` | 200, events sorted by date DESC, ID DESC |
| POST | `/api/events` | 201, created event |
| GET | `/api/events/{eventId}/performances` | 200, version and waiting order |
| POST | `/api/events/{eventId}/performances` | 201, updated version and waiting order |
| POST | `/api/events/{eventId}/performances/{performanceId}/move` | 200, updated version and waiting order |
| POST | `/api/events/{eventId}/performances/{performanceId}/cancel` | 200, updated version and waiting order |

Create night body:

```json
{"eventName":"Friday at Mill’s","eventDate":"2026-11-13","organiserName":"Mill"}
```

Event response:

```json
{"eventId":"1","eventName":"Friday at Mill’s","eventDate":"2026-11-13","organiserName":"Mill","queueVersion":"0","createdAt":"2026-11-05T10:00:00Z"}
```

Add performance body (use actual IDs and the latest queue version):

```json
{"singerId":"6","songId":"1","queueVersion":"0"}
```

Queue response:

```json
{
    "eventId":"1",
    "queueVersion":"1",
    "performances":[{
        "performanceId":"1","singerId":"6","singerName":"Mill","nickName":"Bandara",
        "songId":"1","songTitle":"Country Roads","artist":"John Denver",
        "queuePosition":"1","status":"WAITING","createdAt":"2026-11-05T10:01:00Z"
    }]
}
```

Move body: `{"direction":"UP","queueVersion":"1"}` or `DOWN`. Cancel body: `{"queueVersion":"1"}`. These examples are alternatives, not a sequence with automatically valid versions. Fetch the current queue before making another change.

Errors use the existing `code`, `message`, optional `fieldErrors` envelope:

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Invalid fields, references or boundary move |
| 400 | `INVALID_REQUEST` | Invalid JSON shape/types/unknown fields |
| 404 | `NOT_FOUND` | Night/performance unavailable in the configured workspace |
| 409 | `STALE_QUEUE` | Refresh before retrying the intended change |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | JSON required |
| 503 | `STORAGE_UNAVAILABLE` | Storage temporarily unavailable |
| 500 | `INTERNAL_ERROR` | Unexpected failure without internal details |

## Verification

```sh
./scripts/check.sh
```

Tests cover event validation, strict JSON contracts, lifecycle/version responses, fresh/upgrade migrations, preservation of existing singer/song data, scoped foreign keys, restricted deletion, persisted cancellation, atomic swaps, stale changes, concurrent additions and competing movement/cancellation. Database tests run only against disposable PostgreSQL containers. The existing singer/song regression tests remain in the full build.

Manual browser checks:

1. Create a night, including blank/invalid input and keyboard submission.
2. Add multiple performances, including repeated singer/song choices.
3. Search the song selector; navigate to Singers/Songs and back.
4. Move first/middle/last entries and confirm boundary buttons are disabled.
5. Cancel a removal confirmation, then confirm removal; inspect persistence after refresh.
6. Create another night, switch between them, and check distinct orders.
7. Change the same night in two tabs; verify stale changes require refresh.
8. Verify scrollable lists and a narrow layout without horizontal overflow.
9. Check connection failure/recovery; unfinished form values must survive failures.
