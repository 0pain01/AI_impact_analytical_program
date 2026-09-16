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

A bare `docker compose up -d` (no service names) starts **every** service defined in
`docker-compose.yml`, not just Postgres/RabbitMQ — that now includes the two containerized
connectors (ADR-0005/ADR-0007) and the real Jenkins CI server they need. Provides:

| Service | Endpoint | Credentials (local default) |
|---|---|---|
| PostgreSQL 16 | `localhost:5442` (host port; container 5432), db `aiimpacteval` | `aiimpacteval` / `aiimpacteval_local` |
| RabbitMQ 3 | `localhost:5672` (AMQP), `localhost:25672` (management UI) | `aiimpacteval` / `aiimpacteval_local` |
| `gitlab` (connector) | `localhost:8088` | Set `GITLAB_TOKEN` in `.env` first (see connector-gitlab's README) — it still starts fine without one, just rate-limited |
| `jenkins` (real CI server) | `localhost:9090` | `jenkins/jenkins:lts`, reuses the pre-existing `jenkins_home` volume (ADR-0007) — real job history, not a fresh instance |
| `connector-jenkins` | `localhost:8086` | Talks to the `jenkins` service above over the compose network; set `JENKINS_USERNAME`/`JENKINS_API_TOKEN` in `.env` |

If you only want Postgres/RabbitMQ (e.g. no GitLab/Jenkins credentials yet and you'd rather not
build those images), name the services explicitly instead of the bare command above:
`docker compose up -d postgres rabbitmq`.

> Management UI defaults to 25672, not RabbitMQ's usual 15672 — 15672 falls inside a Windows
> Hyper-V/WSL reserved port-exclusion range on some machines, blocking Docker from binding it.
> Override with `RABBITMQ_MGMT_PORT` if that doesn't affect you. AMQP (5672, what the services
> actually connect over) is unaffected either way.

## Run the platform

All 7 plain-process backend services + infra (which now also brings up the two containerized
connectors — `gitlab`, `connector-jenkins` — and the Jenkins CI server they need, since a bare
`docker compose up` starts every service in `docker-compose.yml`) in one command (builds with
`mvn package`, starts each plain-process service detached, waits for `/actuator/health`, logs to
`/tmp/aiimpacteval-<service>.log`):

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
