# MillodySingQueue

*Every singer. Every song. Your night.*

MillodySingQueue is a karaoke night organiser that helps a host manage singers, their song choices, and the performance queue. Returning singers can revisit their history and choose a favourite song for another night.

This is a personal showcase project, developed incrementally through small, tested changes and reviewed pull requests.

## Project status

Task 1001 establishes the application foundation: a Java/GWT page calls the Kotlin/Ktor health endpoint and displays the connection status. Ktor serves the compiled frontend and JSON API together. Singer management, persistence, playback, containers, and AWS deployment are planned for later increments.

## Quick start

Prerequisite: **JDK 21**, with `JAVA_HOME` pointing to it and Java available on `PATH`. The first build downloads the pinned build tools and dependencies. You do not need globally installed Maven, Gradle, PostgreSQL, Node.js, or a running Colima instance for this increment.

On macOS, select the installed JDK if needed:

```zsh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

From the repository root, build and test both components:

```sh
cd backend
./gradlew clean build
```

Gradle invokes `frontend/mvnw clean verify` when frontend inputs change, compiles the GWT application, includes its public assets in the backend resources, runs the backend tests, and creates application distributions. The frontend clean prevents deleted assets lingering in later packages. Generated files remain under `frontend/target/` and `backend/build/` and are excluded from Git.

Start the integrated application from `backend/`:

```sh
./gradlew run
```

Open [http://localhost:8080](http://localhost:8080). The page should show **Backend connected**. Use **Check connection** to retry. Requests time out after five seconds; failed HTTP responses, invalid health responses, and network failures display a readable error. Stop the server with **Control + C**.

Check the JSON endpoint separately:

```sh
curl --fail http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP"}
```

This is a process health endpoint, not a database-readiness check; there is no database dependency yet.

### Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `HOST` | `127.0.0.1` | Network interface to bind |
| `PORT` | `8080` | HTTP port |

For example, run `PORT=8081 ./gradlew run` from `backend/` to use another port. The application defaults to localhost because authentication is not implemented yet. Container deployment will explicitly set `HOST=0.0.0.0` in a later increment. Environment variables are read directly; `.env` files are not automatically loaded.

### Run the packaged distribution

From `backend/`:

```sh
./gradlew installDist
./build/install/millodysingqueue/bin/millodysingqueue
```

The distribution contains the backend, compiled frontend resources, dependencies, and launch scripts. It requires Java 21 at runtime. Its launcher can run from another working directory because assets are loaded from the classpath. The frontend WAR is only a build intermediate; Tomcat is not required.

## Repository structure

```text
frontend/                  Java/GWT source, Maven build and wrapper
backend/                   Kotlin/Ktor source, tests, Gradle build and wrapper
.github/workflows/ci.yml    Integrated build and test checks
.editorconfig              Shared formatting defaults
```

The separate project plan is intentionally not included in this repository. AWS infrastructure and local database configuration will be added in their own tasks.

## Build versions

| Tool or framework | Version |
| --- | --- |
| JDK | 21 |
| GWT / frontend source level | 2.13.1 / Java 17 |
| Maven wrapper distribution | 3.10.0 |
| Gradle wrapper distribution | 9.7.0 |
| Kotlin | 2.4.21 |
| Ktor | 3.6.0 |

Use the wrappers for builds. Their distribution checksums are pinned. Backend dependencies are locked in `backend/gradle.lockfile`; intentional dependency changes must update and review the lockfile using `./gradlew build --write-locks` from `backend/`. Frontend dependencies/plugins are declared in `frontend/pom.xml`.

## Tests and CI

From `backend/`, run:

```sh
./gradlew test
```

The build compiles frontend assets if necessary. Ktor tests verify the health JSON/status/content type, cache behaviour, serving the frontend/bootstrap/CSS, and 404 responses for unknown API paths and excluded build metadata. Results are written to `backend/build/reports/tests/test/index.html`.

For a browser smoke check, start the application and verify the success message. Stop the backend while leaving the page open, click **Check connection**, and verify the error message. Restart it and retry to confirm recovery. This check exercises compiled GWT code; backend tests alone do not execute browser JavaScript.

GitHub Actions runs the integrated clean build on pull requests and pushes to `main`, with read-only repository permissions and pinned action revisions. CI results can only be confirmed after the branch is pushed and the workflow runs. Branch protection must be configured separately in GitHub.

## IntelliJ IDEA

1. Open the repository folder.
2. Link/import `backend/build.gradle.kts` as a Gradle project and `frontend/pom.xml` as a Maven project.
3. Select **JDK 21** for the project SDK, Gradle JVM, and Maven runner/importer; use the project wrappers.
4. Run the backend Gradle `run` task. It builds the frontend before starting Ktor. Stop and rerun after editing frontend source.

Specialised GWT/Ktor IDE plugins are optional; the wrapper commands are the reference build workflow. `.editorconfig` uses four spaces for Java/Kotlin/TypeScript and two for YAML. Generated wrapper scripts retain upstream formatting.

## Planned features

- Register singers with a name and optional nickname/email; distinguish singers with the same name using a unique singer ID.
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

Database and AWS setup instructions will be added with their implementation. Keep credentials and local `.env` files outside version control.

## Playback considerations

Embedded video availability depends on YouTube and the video owner. The application must handle unavailable videos and browser playback restrictions. Fullscreen playback and the actual laptop/display/audio combination require a real-device rehearsal.

The initial version does not download video, extract audio, or generate lyrics. It is an independent project and is not affiliated with YouTube or karaoke content providers.
