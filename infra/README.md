# Infra — Local Development Environment

## Prerequisites

- Docker Desktop (or compatible)
- JDK 21+ (compile target is 21; any newer JDK works — e.g. `brew install openjdk`)
- Node.js 20+

## Start local infrastructure

```bash
cd infra
cp .env.example .env   # first time only
docker compose up -d
```

Provides:

| Service | Endpoint | Credentials (local default) |
|---|---|---|
| PostgreSQL 16 | `localhost:5442` (host port; container 5432), db `aiimpacteval` | `aiimpacteval` / `aiimpacteval_local` |
| RabbitMQ 3 | `localhost:5672` (AMQP), `localhost:15672` (management UI) | `aiimpacteval` / `aiimpacteval_local` |

## Run the platform

All 6 backend services + infra in one command (builds with `mvn package`, starts each
service detached, waits for `/actuator/health`, logs to `/tmp/aiimpacteval-<service>.log`):

```bash
./infra/start-backend.sh
cd frontend && npm install && npm run dev   # UI at :5173
```

Stop the backend services with `./infra/stop-backend.sh` (infra containers are left running —
`docker compose -f infra/docker-compose.yml down` to stop those too).

To run a single service instead (e.g. while iterating on it):

```bash
# API core (creates staging/core/mart schemas via Flyway on first start)
cd services && mvn spring-boot:run -pl api-core
```

Local defaults are wired so no configuration is needed beyond `.env`. Real environments use
env vars / secret manager only — never commit credentials (see security standards).

## End-to-end smoke test

After `mvn package` in `services/`:

```bash
./infra/smoke-e2e.sh
```

Boots infra + api-core + ingestion-writer + connector-github, posts a signed fake GitHub
webhook (and a forged one), and verifies the event lands in `staging.raw_event` while the
forgery is rejected. This is the FR-1.8 / PRD F1 verification path and will become the CI
smoke stage.

CI pipeline definitions will live in this directory as they are added.
