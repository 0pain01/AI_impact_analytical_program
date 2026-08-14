# connector-github

Ingestion-layer connector for GitHub (PRD F1, FR-1.1). Publishes raw GitHub data to the
`aiimpacteval.events` exchange per ADR-0003. Owns **no business logic** (ADR-0002).

**Status:** webhook path + PR/commit/workflow-run/team backfill implemented. GitHub Actions
build/deployment data (PRD F3, FR-1.3) flows through this connector — live via the generic
webhook (`workflow_run`, `deployment_status`), history via backfill. Team-structure import
(PRD E2-S2, FR-1.5) fetches org teams + their repos + members and publishes one snapshot per
team for the identity service to normalize. Not yet: GitHub App installation flow (currently
PAT via env), reviews/branches backfill, adaptive rate-limit throttling, webhook-gap healing
poller.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /webhooks/github` | GitHub webhook receiver. Verifies `X-Hub-Signature-256` (HMAC-SHA256, constant-time); 401 on failure. |
| `POST /internal/backfill?owner={o}&repo={r}` | Backfills PRs + commits + workflow runs for the configured window. Internal — invoked by api-core on repo connect. |
| `POST /internal/backfill-teams?org={org}` | Backfills org teams, their repos, and their members. Internal — invoked by api-core on org connect. |
| `GET /actuator/health` | Liveness/readiness. |

## Event types published

`github.<webhook event>` (e.g. `github.pull_request`, `github.push`, `github.workflow_run`)
for live webhooks; `github.pull_request.snapshot` / `github.commit.snapshot` /
`github.workflow_run.snapshot` / `github.team.snapshot` for backfill. Unlike other backfills,
`team.snapshot`'s sourceId includes the current instant rather than an entity timestamp
(GitHub's Teams API exposes no reliable "updated at") — every run publishes a fresh snapshot,
and the identity service's own upsert logic keeps repeated imports safe.

## Configuration (env vars)

| Var | Default | Purpose |
|---|---|---|
| `GITHUB_WEBHOOK_SECRET` | *(empty — all webhooks rejected)* | HMAC secret configured on the GitHub webhook |
| `GITHUB_TOKEN` | *(empty)* | Read-only fine-grained PAT / App installation token for backfill |
| `GITHUB_API_BASE_URL` | `https://api.github.com` | Override for GitHub Enterprise / tests |
| `GITHUB_BACKFILL_DAYS` | `90` | Backfill window (PRD F1) |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | localhost defaults | Queue connection |
| `SERVER_PORT` | `8081` | HTTP port |

## Tests

`mvn test -pl connectors/connector-github` — covers signature verification (valid, wrong
secret, tampered body, malformed/missing header, unconfigured secret) and controller
publish/reject behavior with a fixed clock.
