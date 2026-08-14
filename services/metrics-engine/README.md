# metrics-engine

Semantic/metrics layer (C4 container "Metrics Engine", PRD E3): computes metrics from immutable
staged events and materializes them into `mart.metric_daily`. Sole writer to the mart.

**Status:** all four DORA metrics + PR analytics, each at **repo**, **org** (`*`), and **team**
scope (E4-S2 org → team drill-down — team scope via `core.team_repo`, populated by the identity
service's team import, E2-S2). Full-window recompute every 5 min (configurable) — reproducible
from staging by design. Metrics: `deployment_frequency` (DORA-1), `lead_time_p50_hours`
(DORA-2, heuristic PR-open→first prod deploy after merge), `change_failure_rate` (DORA-3,
deploy followed within 48h by a hotfix/rollback), `mttr_p50_hours` (DORA-4), `pr_velocity`,
`pr_cycle_time_p50_hours`. Team-level percentiles are computed from the underlying per-event
rows joined to `core.team_repo`, never by averaging repo-day medians — percentiles don't
compose that way. Not yet: the full lead-time stage breakdown (needs PR-review + commit-in-PR
ingestion); incident-based CFR (Phase-2 incident connector); RAG target bands; ingestion-lag
freshness metric export.

Formulas are governed by [docs/01-product/metric-definitions.md](../../docs/01-product/metric-definitions.md);
`METRIC_LOGIC_VERSION` bumps whenever a formula changes and is recorded on every row.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /internal/recompute` | On-demand full recompute (smoke tests, post-backfill) |
| `GET /actuator/health` | Liveness/readiness |

## Configuration (env vars)

| Var | Default | Purpose |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local Postgres defaults | Reads `staging`, writes `mart` |
| `METRICS_RECOMPUTE_INTERVAL_MS` | `300000` | Scheduled recompute cadence |
| `METRICS_WINDOW_DAYS` | `90` | Recompute window |
| `METRICS_DEPLOY_WORKFLOW_PATTERN` | `deploy\|release` | Deployment-detection default (E1-S3 mapping rule) |
| `METRICS_HOTFIX_WORKFLOW_PATTERN` | `hotfix\|rollback\|revert` | Remediation-detection default for CFR/MTTR |
| `SERVER_PORT` | `8084` | HTTP port |

## Tests

`mvn test -pl metrics-engine` — Testcontainers suite covering deployment-rule matching,
failed/non-deploy exclusion, latest-state dedup (webhook + snapshot), merge-day attribution,
p50 cycle time, org and team rollups (including that team percentiles come from the raw
per-event population, not an average of repo medians), a repo with no team producing no team
row, and recompute idempotency. Auto-skips without usable Docker (runs in CI);
`infra/smoke-e2e.sh` exercises the same path live.
