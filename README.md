# MillodySingQueue

*Every singer. Every song. Your night.*

MillodySingQueue is a karaoke night organiser that helps a host manage singers, their song choices, and the performance queue. Returning singers can revisit their history and choose a favourite song for another night.

This is a personal showcase project, developed incrementally through small, tested changes and reviewed pull requests.

## Project status

Repository initialised. Application implementation has not started yet. The features and architecture below describe the intended application; there are no application build or run commands available yet.

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

Detailed setup instructions, pinned project dependencies, configuration examples, and build/test commands will be added alongside the corresponding implementation. Keep credentials and local `.env` files outside version control.

## Playback considerations

Embedded video availability depends on YouTube and the video owner. The application must handle unavailable videos and browser playback restrictions. Fullscreen playback and the actual laptop/display/audio combination require a real-device rehearsal.

The initial version does not download video, extract audio, or generate lyrics. It is an independent project and is not affiliated with YouTube or karaoke content providers.
