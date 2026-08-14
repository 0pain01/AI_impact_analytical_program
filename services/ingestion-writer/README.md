# ingestion-writer

Staging consumer (ingestion layer): subscribes to **all** events on the `aiimpacteval.events`
exchange (`#` binding) and persists them idempotently into the immutable `staging.raw_event`
table. This is the component that makes the pipeline lose-nothing (FR-1.8): connectors stay
dumb, the queue absorbs outages, this service is the single writer to staging.

**Status:** implemented with DLQ wiring. Not yet: ingestion-lag metric export (15-min
freshness alert), replay tooling.

## Behavior

- Idempotency: `ON CONFLICT ON CONSTRAINT uq_raw_event_natural_key DO NOTHING` — queue
  redeliveries and backfill replays never duplicate rows (ADR-0003).
- Poison messages: rejected without requeue → `staging.events.dlq` (never dropped).
- Staging DDL is owned by api-core's Flyway migrations; this service only writes.

## Configuration (env vars)

| Var | Default |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local Postgres defaults |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | localhost defaults |
| `SERVER_PORT` | `8082` |

## Tests

`mvn test -pl ingestion-writer` — Testcontainers integration test (requires Docker) proving
idempotent writes and payload round-trip against real Postgres 16.

Note: the test auto-skips when the Docker API is unreachable. Docker Desktop with Enhanced
Container Isolation / API-proxy restrictions blocks non-CLI socket clients (HTTP 400 stub
responses) — Testcontainers cannot run there. `infra/smoke-e2e.sh` covers the same path via
the docker CLI; CI must run this test un-skipped.
