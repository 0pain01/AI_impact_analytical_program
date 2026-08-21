# connector-ai-telemetry

Ingestion-layer connector for AI coding-assistant usage — Claude Code and GitHub Copilot today
(PRD E9, AI-01/AI-02/AI-03). Publishes raw usage snapshots to the `aiimpacteval.events` exchange
per ADR-0003. Owns **no business logic** (ADR-0002) — the AI Cost Track computation lives in
api-core's `AiCostTrackQueryService`, reading `staging.ai_usage_state`.

**Status:** backfill only, reading from a local usage-report file per tool (see
[claude-code-usage-report.json](../../../infra/sample-data/claude-code-usage-report.json) /
[copilot-usage-report.ndjson](../../../infra/sample-data/copilot-usage-report.ndjson) for the
sample data this was built and verified against). This is a deliberate seam, not a stopgap
shortcut: `ClaudeCodeUsageBackfillService.readReport()` /
`CopilotUsageBackfillService.readReportLines()` are the ONLY methods a real integration needs to
replace — swap the file read for an authenticated call to Anthropic's Admin API usage-report
endpoint / GitHub's Copilot Metrics API, and every downstream event, the whole projection table,
and the AI Cost Track API needs zero changes, since the file's shape already matches each API's
real response verbatim.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /internal/backfill/claude-code` | Reads the configured Claude Code usage-report file, publishes one `usage.snapshot` event per `(actor.email_address, date)` record. |
| `POST /internal/backfill/copilot` | Reads the configured Copilot NDJSON usage export, publishes one `usage.snapshot` event per `(user_login, day)` line. |
| `GET /actuator/health` | Liveness/readiness. |

## Event types published

`claude_code.usage.snapshot` (sourceId `claude_code:{email}:{day}`) and
`copilot.usage.snapshot` (sourceId `copilot:{login}:{day}`) — idempotent per ADR-0003: re-running
a backfill re-publishes the same day's row unchanged, which is a no-op once staged.

## How ingestion-writer projects it

`StagingEventWriter.upsertAiUsageState` normalizes both tools' very different payload shapes into
one common `staging.ai_usage_state` row per `(source, actor_key, day)`:
- **Claude Code**: real per-day dollar cost, summed across every model in `model_breakdown[]`.
- **Copilot**: no per-request cost exists in its export (flat-fee seat product) — a configurable
  per-seat monthly price (`COPILOT_MONTHLY_SEAT_COST_USD`, ingestion-writer's own env var) is
  allocated across active days only, never charged on a day with zero activity.

## Configuration (env vars)

| Var | Default | Purpose |
|---|---|---|
| `CLAUDE_CODE_USAGE_FILE` | *(empty — backfill fails fast)* | Path to a Claude Code usage-report JSON file |
| `COPILOT_USAGE_FILE` | *(empty — backfill fails fast)* | Path to a Copilot usage-export NDJSON file |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | localhost defaults | Queue connection |
| `SERVER_PORT` | `8087` | HTTP port |

## Tests

None yet — a known gap, not an oversight. Should cover: both backfill services against a small
fixture file (one record each), the malformed-NDJSON-line skip path in
`CopilotUsageBackfillService`, and `StagingEventWriter`'s cost normalization (real Claude Code
cost sum, Copilot active-day-only seat allocation, zero cost on an inactive day).
