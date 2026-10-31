# MillodySingQueue

*Every singer. Every song. Your night.*

MillodySingQueue is a karaoke night organiser that helps a host manage singers, their song choices, and the performance queue. Returning singers can revisit their history and choose a favourite song for another night.

This is a personal showcase project, developed incrementally through small, tested changes and reviewed pull requests.

## Project status

Task 1003 adds singer registration and a searchable directory to our Java/GWT frontend and Kotlin/Ktor JSON API. Singers are stored in PostgreSQL through Exposed SQL DSL and HikariCP, using the restricted application role. Liquibase remains responsible for explicit schema migrations. Songs, karaoke nights, editing/deleting, authentication and AWS deployment are later increments. This version is for localhost use only.

## Quick start

Prerequisites: **JDK 21**, with `JAVA_HOME` pointing to it and Java available on `PATH`; Docker CLI and a running Docker-compatible engine (Colima locally) for database integration tests. The first build downloads pinned build tools, dependencies and test images. You do not need globally installed Maven, Gradle, PostgreSQL, Liquibase or Node.js.

On macOS, select the installed JDK if needed:

```zsh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

From the repository root, build and test both components:

```sh
./scripts/check.sh
```

Gradle invokes `frontend/mvnw clean verify` when frontend inputs change, compiles the GWT application, includes its public assets in the backend resources, runs the backend tests, and creates application distributions. The frontend clean prevents deleted assets lingering in later packages. Generated files remain under `frontend/target/` and `backend/build/` and are excluded from Git.

Start the integrated application from the repository root:

```sh
# Complete the Local database setup below first.
./scripts/run.sh
```

Open [http://localhost:8080](http://localhost:8080). The page shows singer registration and the directory, with our cream-orange, white and teal theme. Register a singer, refresh the page and search by name, nickname or email. See [Singer management](docs/singer-management.md) for the API, validation rules and browser checks. Stop the server with **Control + C**.

Check the JSON endpoint separately:

```sh
curl --fail http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP"}
```

This is a process health endpoint, not a database-readiness check. Startup requires a reachable database; later database failures are reported by the singer API.

### Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `HOST` | `127.0.0.1` | Network interface to bind |
| `PORT` | `8080` | HTTP port |
| `APPLICATION_DB_URL` | Required | PostgreSQL JDBC URL |
| `APPLICATION_DB_USER` | Required | Restricted application login (`millody_app` locally) |
| `APPLICATION_DB_PASSWORD` | Required | Application database password |

For example, run `PORT=8081 ./scripts/run.sh` from the repository root to use another port. The application defaults to localhost because authentication is not implemented yet. Container deployment will explicitly set `HOST=0.0.0.0` in a later increment. Ktor reads environment variables directly. The local `scripts/run.sh` helper loads the trusted `.env` and supplies local URL/user defaults for existing task 1002 files. Direct Gradle and packaged launches require the three application variables to be exported.

### Run the packaged distribution

From the repository root, with PostgreSQL started and migrated:

```sh
./backend/gradlew -p backend installDist
set -a
. ./.env
APPLICATION_DB_URL=${APPLICATION_DB_URL:-jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5432}/millodysingqueue}
APPLICATION_DB_USER=${APPLICATION_DB_USER:-millody_app}
set +a
./backend/build/install/millodysingqueue/bin/millodysingqueue
```

The distribution contains the backend, compiled frontend resources, dependencies, and launch scripts. It requires Java 21 at runtime. Its launcher can run from another working directory because assets are loaded from the classpath. The frontend WAR is only a build intermediate; Tomcat is not required.

## Repository structure

```text
frontend/                  Java/GWT source, Maven build and wrapper
backend/                   Kotlin/Ktor source, tests, Gradle build and wrapper
.github/workflows/ci.yml    Integrated build and test checks
.editorconfig              Shared formatting defaults
```

Local database setup and administration are documented in [Local PostgreSQL and Liquibase](docs/local-database.md). The separate project plan is intentionally not included in this repository. AWS infrastructure will be added in a later task.

## Local database

From the repository root:

```sh
colima start --cpu 2 --memory 2
./scripts/setup-local-env.sh
docker compose up -d --wait postgres
./scripts/db.sh validate
./scripts/db.sh update-sql
./scripts/db.sh update
./scripts/db.sh status
```

The setup script creates an ignored `.env` with independent random passwords and refuses to replace an existing file. PostgreSQL binds to loopback only and keeps its data in a named volume. The migration role owns the schemas; the application role receives data permissions only. See the [database guide](docs/local-database.md) for DataGrip, role setup, migration rules, tests, backup/restore, and destructive reset instructions.

## Build versions

| Tool or framework | Version |
| --- | --- |
| JDK | 21 |
| GWT / frontend source level | 2.13.1 / Java 17 |
| Maven wrapper distribution | 3.10.0 |
| Gradle wrapper distribution | 9.7.0 |
| Kotlin | 2.4.21 |
| Ktor | 3.6.0 |
| PostgreSQL image | 17.11 Bookworm, digest-pinned |
| Liquibase | 5.0.4 |
| PostgreSQL JDBC | 42.7.14 |
| Testcontainers | 2.0.5 |
| Exposed SQL DSL | 1.6.0 |
| HikariCP | 7.1.0 |

Use the wrappers for builds. Their distribution checksums are pinned. Backend dependencies are locked in `backend/gradle.lockfile`; intentional dependency changes must update and review the lockfile using `./scripts/check.sh build --write-locks` from the repository root. Frontend dependencies/plugins are declared in `frontend/pom.xml`. Keep the PostgreSQL image in Compose and the integration test aligned when upgrading it.

## Tests and CI

From `backend/`, run:

```sh
./gradlew test
```

The build compiles frontend assets if necessary. Ktor tests verify the health JSON/status/content type, cache behaviour, serving the frontend/bootstrap/CSS, and 404 responses for unknown API paths and excluded build metadata. Results are written to `backend/build/reports/tests/test/index.html`.

Singer service/API tests cover normalisation, validation, JSON errors, duplicate-email conflicts and storage failures. For compiled GWT browser checks, follow [Singer management verification](docs/singer-management.md#verification); backend tests alone do not execute browser JavaScript.

The complete build also runs database integration tests against an automatically created PostgreSQL container, separate from the Compose development database. Use `./scripts/check.sh` from the repository root, or `./scripts/check.sh integrationTest` for only the database suite. Docker is required for these checks; no tests are silently skipped. See the [database testing instructions](docs/local-database.md#tests-and-ci).

GitHub Actions runs the integrated clean build on pull requests and pushes to `main`, with read-only repository permissions and pinned action revisions. CI results can only be confirmed after the branch is pushed and the workflow runs. Branch protection must be configured separately in GitHub.

## IntelliJ IDEA

1. Open the repository folder.
2. Link/import `backend/build.gradle.kts` as a Gradle project and `frontend/pom.xml` as a Maven project.
3. Select **JDK 21** for the project SDK, Gradle JVM, and Maven runner/importer; use the project wrappers.
4. Configure the three `APPLICATION_DB_*` environment variables in your backend Gradle `run` configuration, or use `./scripts/run.sh` in the terminal. It builds the frontend before starting Ktor. Stop and rerun after editing frontend source.

Specialised GWT/Ktor IDE plugins are optional; the wrapper commands are the reference build workflow. `.editorconfig` uses four spaces for Java/Kotlin/TypeScript and two for YAML. Generated wrapper scripts retain upstream formatting.

## Planned features

- Edit singer details and introduce authenticated admin access.
- Create karaoke nights and assign songs to singers using YouTube links.
- Reorder the queue and start, pause, complete, or skip performances.
- Use a laptop admin window alongside an audience window displaying the embedded YouTube player on an external screen.
- Preserve performance history and add previous songs to a new night's queue.

The host manages registration and the queue. Guests do not need accounts in the initial version. Songs use the music and on-screen lyrics already present in the selected videos. Automatic YouTube search is deferred.

## Technology direction

| Area | Technology |
| --- | --- |
| Frontend | Java with GWT |
| Backend | Kotlin with Ktor and a JSON API |
| Database | PostgreSQL |
| Database access | Exposed SQL DSL and HikariCP |
| Schema migrations | Liquibase |
| Java baseline | JDK 21; initial GWT source language level 17 |
| Frontend build | Maven |
| Backend build | Gradle Kotlin DSL with a project wrapper |
| Local containers | Colima, Docker CLI, and Docker Compose |
| AWS infrastructure | AWS CDK v2 using TypeScript |
| Intended AWS deployment | ECR, ECS on Fargate, and RDS for PostgreSQL |

GWT frontend code is written in Java; Ktor backend code is written in Kotlin. They communicate over HTTP using JSON. The initial deployment will package the compiled GWT assets with Ktor, serving the frontend and API from the same origin. YouTube streams directly to the browser through its official embedded player.

## Development approach

- Keep `main` buildable and develop on short-lived feature branches.
- Make small pull requests that deliver reviewable increments.
- Add meaningful tests with each feature: unit tests for rules, API/database integration tests, and browser checks for core workflows.
- Validate the two-screen playback flow early, then build singer management, persistence, queue management, and history.
- Introduce reproducible infrastructure, CI/CD, monitoring, and recovery documentation for the AWS showcase deployment.

## Local tooling

Development uses IntelliJ IDEA, JDK 21, Maven, and Colima with Docker CLI/Compose. Node.js and npm will support the TypeScript CDK infrastructure. PostgreSQL will run in a container rather than being installed directly on macOS.

See [Local PostgreSQL and Liquibase](docs/local-database.md) for database setup. AWS setup instructions will be added with its implementation. Keep credentials and local `.env` files outside version control.

## Playback considerations

Embedded video availability depends on YouTube and the video owner. The application must handle unavailable videos and browser playback restrictions. Fullscreen playback and the actual laptop/display/audio combination require a real-device rehearsal.

The initial version does not download video, extract audio, or generate lyrics. It is an independent project and is not affiliated with YouTube or karaoke content providers.
