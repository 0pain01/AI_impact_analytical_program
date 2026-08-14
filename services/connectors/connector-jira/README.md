# connector-jira

Ingestion-layer connector for Jira (PRD F2, FR-1.2). Publishes raw Jira data to the
`aiimpacteval.events` exchange per ADR-0003. Owns **no business logic** (ADR-0002).

**Status:** webhook path + issue backfill (with changelogs) implemented. Not yet: sprint/board
backfill via the Agile API, adaptive rate-limit throttling, webhook-gap healing poller.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /webhooks/jira?token={secret}` | Jira webhook receiver. Jira doesn't HMAC-sign webhooks, so the URL carries a shared token (constant-time compared; fail-closed when unconfigured). 401 on failure. |
| `POST /internal/backfill?projectKey={KEY}` | Backfills issues updated in the configured window, `expand=changelog` (status transitions feed ticket lead time, FR-8.2.3). |
| `GET /actuator/health` | Liveness/readiness. |

## Event types published

`jira.jira:issue_created` / `jira.jira:issue_updated` etc. (webhook `webhookEvent` values)
for live webhooks; `jira.issue.snapshot` for backfill. Webhook idempotency key is the
SHA-256 body hash (Jira sends no delivery GUID); backfill uses `issue:{id}:{updated}`.

## Configuration (env vars)

| Var | Default | Purpose |
|---|---|---|
| `JIRA_WEBHOOK_SECRET` | *(empty — all webhooks rejected)* | Shared token carried in the webhook URL |
| `JIRA_BASE_URL` | *(empty)* | e.g. `https://yourorg.atlassian.net` |
| `JIRA_EMAIL` / `JIRA_API_TOKEN` | *(empty)* | Atlassian Cloud basic auth (read-only) |
| `JIRA_BACKFILL_DAYS` | `90` | Backfill window |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | localhost defaults | Queue connection |
| `SERVER_PORT` | `8083` | HTTP port |

## Tests

`mvn test -pl connectors/connector-jira` — token verification (match, mismatch, missing,
fail-closed), controller publish/reject behavior, and stable idempotency key for identical
redeliveries.
