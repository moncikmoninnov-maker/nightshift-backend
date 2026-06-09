# NightShift Launcher

Desktop launcher for NightShift Client Beta. Multi-module Gradle project:

- `launcher-shared` — DTOs shared between client and backend (kotlinx-serialization).
- `launcher-client` — Compose Desktop UI, packaged as a Windows `.exe` via jpackage.
- `launcher-backend` — Ktor + Exposed + PostgreSQL, deployed to a Hetzner VPS.

## Requirements

- JDK 21 (Temurin recommended)
- Docker (for local PostgreSQL)
- Gradle is provided via the wrapper (`./gradlew`)

## Local PostgreSQL

A `docker-compose.yml` in the repo root brings up Postgres 16 with the database
`nightshift` and matching credentials used by `application.conf`.

```bash
# Start in background
docker compose up -d

# Tail logs
docker compose logs -f

# Stop and remove containers (data is preserved in the named volume)
docker compose down
```

The data volume is named `nightshift_pg_data`. To wipe it and start fresh:

```bash
docker compose down -v
```

## Building

```bash
# From the repo root
./gradlew build

# Run the backend (after Postgres is up)
./gradlew :launcher-backend:run

# Run the launcher in development mode
./gradlew :launcher-client:run

# Package the launcher as a Windows .exe (Windows host required)
./gradlew :launcher-client:packageExe
```

## Configuration

Backend reads `launcher-backend/src/main/resources/application.conf` and overrides
the following from environment variables:

| Variable | Description | Default |
| --- | --- | --- |
| `PORT` | HTTP port | `8080` |
| `DB_HOST` | Postgres host | `localhost` |
| `DB_PORT` | Postgres port | `5432` |
| `DB_NAME` | Database name | `nightshift` |
| `DB_USER` | Database user | `nightshift` |
| `DB_PASSWORD` | Database password | `nightshift_dev` |
| `SESSION_SECRET` | Session-token signing secret | placeholder (replace in prod) |
| `ADMIN_TOKEN` | Admin endpoints bearer token | empty (admin disabled) |
| `MIN_CLIENT_VERSION` | Minimum supported launcher version | `1.0.0` |

## Module layout

```
nightshift-launcher/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── docker-compose.yml
├── launcher-shared/
│   └── src/main/kotlin/fun/nightshift/launcher/shared/dto/
├── launcher-client/
│   └── src/main/kotlin/fun/nightshift/launcher/client/Main.kt
└── launcher-backend/
    └── src/main/kotlin/fun/nightshift/launcher/backend/Application.kt
```
