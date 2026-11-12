# Saving songs and searching the library

## Scope

Task 1004 adds a **Save a song** form, searchable song library, and Singers/Songs navigation. It stores song metadata and a YouTube video ID. It does not assign songs to singers, create nights, order performances, search YouTube, or play embedded videos. Task 1006 adds assignments to events through [Performance management](performance-management.md); playback and other capabilities remain separate increments.

Both pages keep the cream-orange background and white cards. Primary text and headings use navy `#183153`; section labels and primary buttons remain teal. The shared navigation uses `#singers` and `#songs` so refresh, bookmarks, and browser Back/Forward work. Task 1006 makes Performances the default for empty/unrecognised fragments. Switching screens keeps unfinished forms and search text in memory; a full page reload clears unfinished input.

Task 1005 scopes these records and duplicate rules to the configured workspace. See [Workspace ownership](workspace-management.md) for setup and the authentication boundary.

## Updating locally

For an existing checkout, first follow the [workspace upgrade instructions](workspace-management.md#local-setup) to configure `APPLICATION_WORKSPACE_ID`. From the repository root, with Colima running:

```sh
docker compose up -d --wait postgres
./scripts/db.sh update
./scripts/run.sh
```

Stop a running application with Control+C before restarting. The new `003-create-songs.sql` migration creates `app.songs` without altering singer records or previous changesets. No database reset is needed. The migration role's existing default privileges grant the application data access to the new table. Ktor continues to use the restricted application role and never applies migrations itself.

Open [Songs](http://localhost:8080/#songs), or select **Songs** in the header. **Singers** returns to the existing registration page.

## Saving a song

All three fields are required:

| Field | Rules |
| --- | --- |
| Song title | Trimmed, nonblank, maximum 200 characters, no control characters |
| Artist | Trimmed, nonblank, maximum 200 characters, no control characters |
| YouTube link or video ID | Trimmed, maximum 2048 characters; must resolve to an 11-character ID containing letters, digits, underscores or hyphens |

Supported input examples, all resolving to the same video:

```text
7aNW9hRKYA8
https://www.youtube.com/watch?v=7aNW9hRKYA8
https://www.youtube.com/watch?v=7aNW9hRKYA8&list=RD7aNW9hRKYA8&t=3s
https://youtu.be/7aNW9hRKYA8?si=share-token
https://www.youtube.com/embed/7aNW9hRKYA8
https://www.youtube.com/shorts/7aNW9hRKYA8
https://www.youtube.com/live/7aNW9hRKYA8
https://www.youtube-nocookie.com/embed/7aNW9hRKYA8
```

The supported YouTube watch/embed/shorts/live hosts are `youtube.com`, `www.youtube.com`, `m.youtube.com`, and `music.youtube.com`. Short links support `youtu.be` and `www.youtu.be`. Privacy-enhanced embed URLs support `youtube-nocookie.com` and `www.youtube-nocookie.com`. HTTP/HTTPS links and links without a scheme are accepted. User credentials, explicit ports, unrelated/spoofed hosts, unsupported paths and multiple `v` parameters are rejected.

Only the extracted ID is stored; playlist, timestamp, sharing and other query parameters are discarded. IDs are case-sensitive and are never lowercased. The backend parses the supplied string locally and does not fetch arbitrary URLs or call YouTube. Title and artist are supplied by the admin, not retrieved from YouTube. Saving does not prove that a video exists, includes lyrics, or permits embedding.

The database enforces one library entry per video ID, including concurrent saves. Saving the same video through another URL returns a duplicate error on the video field. Another video ID with the same title/artist is allowed, supporting alternate karaoke versions.

## Frontend behaviour

- Success shows **Song saved successfully.**, clears the form/search and reloads the library.
- Failed validation preserves input, displays field messages and focuses the first invalid field.
- Saving disables the form to prevent duplicate clicks. POST requests are not automatically retried.
- Requests time out after 15 seconds. If a save cannot be confirmed, refresh the library before trying again; the server may already have saved it.
- The total counts all saved songs. Search filters the loaded songs by title or artist, ignoring case, with a separate filtered count.
- Loading, empty library, no search matches, malformed responses and connection failures have distinct messages. A failed refresh retains the last loaded library with an explanatory message.
- User input is rendered as text, not HTML. Forms have associated labels, live status messages and visible keyboard focus; the layout stacks at narrow widths.
- **View on YouTube** opens the validated video URL in a separate tab. No embedded player or automatic video loading is included.

The initial library loads all songs, suitable for a small karaoke collection. Pagination and server search can be added when usage requires them.

## API

### POST /api/songs

Use `Content-Type: application/json`:

```json
{
    "title": "Take Me Home, Country Roads",
    "artist": "John Denver",
    "youtubeVideo": "https://www.youtube.com/watch?v=7aNW9hRKYA8"
}
```

Returns **201 Created**:

```json
{
    "songId": "1",
    "title": "Take Me Home, Country Roads",
    "artist": "John Denver",
    "youtubeVideoId": "7aNW9hRKYA8",
    "createdAt": "2026-10-31T10:00:00Z"
}
```

IDs are strings to preserve BIGINT precision in JavaScript; timestamps are UTC ISO-8601 strings. Missing/null fields receive field validation errors; populated fields must be JSON strings. Unknown properties, wrong value types and malformed JSON are rejected.

### GET /api/songs

Returns **200 OK**, with an array of the responses above, or `[]`. Ordering is case-insensitive title, case-insensitive artist, then song ID. Song endpoint responses use `Cache-Control: no-store`.

### Errors

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Field errors keyed by `title`, `artist` or `youtubeVideo` |
| 400 | `INVALID_REQUEST` | Malformed JSON, wrong value types or unknown properties |
| 409 | `VIDEO_ALREADY_EXISTS` | Duplicate video, with a `youtubeVideo` field error |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Use `application/json` |
| 503 | `STORAGE_UNAVAILABLE` | Song storage unavailable; offer manual retry |
| 500 | `INTERNAL_ERROR` | Unexpected failure; internal details are not returned |

Errors follow the existing `code`, `message`, optional `fieldErrors` structure used by the singer API. `/api/health` remains a process health check. Authentication/authorisation remain a later increment; keep this version on localhost.

## Architecture and verification

Song routes handle HTTP/JSON; the service handles input rules and YouTube ID extraction; the Exposed repository handles transactions and database errors. It shares the managed HikariCP pool and restricted role with singer storage. Liquibase owns schema changes. PostgreSQL uniqueness violations are identified through structured constraint metadata and translated into 409 errors.

GWT has separate Singer and Song screens and resource-specific API clients. Shared widgets handle form semantics and shared JSON transport handles status codes, timeouts and error decoding. Existing singer behaviour remains covered by regression tests and browser checks.

```sh
./scripts/check.sh
```

Tests cover supported/invalid/spoofed links, normalisation, lengths, JSON contracts, duplicates, database constraints/permissions, persistence after reopening a pool and concurrent saves. Integration tests use disposable PostgreSQL containers, not the development database.

Browser acceptance checks:

1. Open Songs; confirm the empty-library state on a fresh database.
2. Save Country Roads using the full watch URL. Refresh and confirm it remains.
3. Save the same ID through a short URL; check the video field error and preserved form values.
4. Save a different ID with the same title/artist; both versions should be allowed.
5. Search by title and artist; test no matches and clearing the search.
6. Submit missing fields and an invalid link; check messages, focus and keyboard submission.
7. Enter unfinished text, switch to Singers and back, and confirm the text remains. Check browser Back/Forward and reload on `#songs`.
8. Check singer registration, nickname conflicts and search still work.
9. Stop/restart Ktor and check library refresh failure/recovery; inspect the narrow layout.
