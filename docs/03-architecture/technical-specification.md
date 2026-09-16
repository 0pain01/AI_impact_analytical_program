# Technical Specification — AI Impact Evaluation

**Status:** reflects the running system as of 2026-09-13. This document is the internal
engineering design reference — data model, API contract, algorithms, security mechanics, and
testing/CI strategy. For the C4 diagrams and high-level container map, see
[system-architecture.md](system-architecture.md); for what each service does and full
cloud-deployment steps, see
[04-operations/deployment-guide.md](../04-operations/deployment-guide.md); for what the product
does from a user's perspective, see the
[Functional Specification](../01-product/functional-specification.md).

## 1. Technology stack

| Layer | Choice | Version | Why (ADR-0001) |
|---|---|---|---|
| Frontend | React + TypeScript (strict) + Tailwind + Recharts | React 18 | Internal expertise, BRD §11.2 |
| Backend | Java + Spring Boot (Web, Data JDBC, Security, AMQP) | Java 21, Spring Boot 3.3.5 | Internal expertise, BRD §11.2 |
| Database | PostgreSQL | 16 | Single store for MVP (ADR-0002); `staging`/`core`/`mart` schemas |
| Message queue | RabbitMQ | 3 | Retry/DLQ semantics out of the box (ADR-0002); Kafka is a scale-revisit, not a v1 need |
| Migrations | Flyway | — | Forward-only, expand/contract; owned entirely by `api-core` |
| Auth | JWT (RS256), Spring Security resource server | — | ADR-0004 |
| Build | Maven (multi-module reactor), Vite | — | |
| Containerization | Docker (multi-stage), Docker Compose | — | ADR-0005; currently only `connector-gitlab` |

## 2. Repository / module layout

```
services/
  platform-common/       shared library: EventEnvelope, EventTopology, TimeoutRestClients
  api-core/               auth, RBAC, all dashboard + admin APIs, Flyway migrations (owns schema)
  metrics-engine/         DORA/PR metric computation, writes mart.metric_daily
  identity-service/       contributor identity resolution, team/group import
  ingestion-writer/       sole writer to staging.* — consumes every event
  connectors/
    connector-github/     GitHub + GitHub Actions
    connector-gitlab/     GitLab + GitLab CI/CD (the one Dockerized service, ADR-0005)
    connector-jira/       Jira
    connector-jenkins/    Jenkins
    connector-ai-telemetry/  Claude Code + GitHub Copilot usage
frontend/                 React SPA
infra/                    docker-compose.yml, start/stop scripts, sample data
docs/                     this documentation tree
```

Every backend module is an independent Maven artifact under one reactor `pom.xml`
(`services/pom.xml`), each a standalone Spring Boot application with its own `main`, own
`application.yml`, own port. There is no shared runtime process — "the backend" is nine separate
JVMs (ten once the frontend's build step is counted) that only ever communicate via RabbitMQ
(connectors → ingestion-writer/identity-service) or direct HTTP (api-core → a connector's
`/internal/backfill`, frontend → api-core).

## 3. Event contract (ADR-0003)

Every connector wraps every entity it publishes in the same envelope, defined once in
`platform-common`:

```java
record EventEnvelope(
    String source,            // "github" | "gitlab" | "jira" | "jenkins" | "claude_code" | "copilot"
    String sourceId,           // idempotency key, source-specific shape (see below)
    String eventType,          // e.g. "pull_request.snapshot", "merge_request_approval.snapshot"
    Instant receivedAt,
    String connectorVersion,
    JsonNode payload            // the vendor's own object, generally unmodified
)
```

Published to a single topic exchange, `aiimpacteval.events`, with the routing key derived from
`source`/`eventType`. Two consumers bind `#` (everything, no filtering at the broker level):
`ingestion-writer` (`staging.events` queue) and `identity-service` (`identity.events` queue) —
adding either consumer required zero connector changes, which is the entire point of a
topic-exchange fan-out over point-to-point queues per connector.

**Idempotency keys, by source** (all designed so a redelivery or a re-run backfill is a safe
no-op):
| Source / entity | `sourceId` shape |
|---|---|
| GitHub PR / commit / workflow run | `pull_request`/`commit`/`workflow_run` id, or a composite including an updated-at timestamp for entities with no stable revision id |
| GitHub PR review | `review:{review_id}` |
| GitLab merge request / commit / pipeline | `mr:{id}:{updated_at}` / `commit:{sha}` / `pipeline:{id}:{updated_at}` |
| GitLab MR approval | `mr_approval:{merge_request_id}:{username}` — one event per approver, GitLab has no native "review id" concept the way GitHub does |
| Jira issue (webhook) | SHA-256 of the raw body (Jira sends no delivery GUID) |
| Jira issue (backfill) | `issue:{id}:{updated}` |
| Jenkins build | `jenkins:{jobName}:{buildNumber}` (build numbers reset per-job, not globally, in Jenkins) |
| AI usage (Claude Code / Copilot) | `claude_code:{email}:{day}` / `copilot:{login}:{day}` |

The natural key `(source, source_id, event_type)` is a unique constraint on
`staging.raw_event`; `ingestion-writer`'s insert uses `ON CONFLICT ... DO NOTHING` against it,
which is the entire idempotency mechanism — no application-level dedup logic, no distributed
lock, just a database constraint.

## 4. Data model

Three Postgres schemas, one instance (ADR-0002). Every schema's DDL is owned exclusively by
`api-core`'s Flyway migrations (`services/api-core/src/main/resources/db/migration/`), even
though other services read from or write to `staging`/`core` at runtime.

### 4.1 `staging` — immutable log + typed projections

| Table | Written by | Purpose |
|---|---|---|
| `raw_event` | `ingestion-writer` (insert-only, never updated) | The source of truth. Every event ever received, forever. Unique on `(source, source_id, event_type)`. |
| `pull_request_state` | `ingestion-writer` | Latest known state per PR/MR (GitHub and GitLab, `gitlab:`-prefixed repo values for the latter) — repo, number, title, author, state, timestamps, `requested_reviewers[]`, `ai_assisted` flag (V13) |
| `pull_request_review_state` | `ingestion-writer` | Latest state per review/approval — repo, pr_number, review_id, reviewer_login, state (`APPROVED`/`CHANGES_REQUESTED`/`COMMENTED`/`DISMISSED` for GitHub; always `APPROVED` for GitLab), submitted_at |
| `workflow_run_state` | `ingestion-writer` | Latest state per CI run — shared by GitHub Actions, GitLab pipelines, **and** Jenkins builds; `conclusion` normalized to one lowercase vocabulary across all three so `metrics-engine`'s DORA queries never need to know which tool built a given commit |
| `jira_issue_state` | `ingestion-writer` | Latest state per issue — project_key, issue_type, status, status_category, priority, assignee, reporter, labels[], due_date, timestamps, a heuristic `reopened` flag (Investment Profile's Rework signal; V10, widened by V14 for the Jira Work Items dashboard — standard fields only, no instance-specific customfield IDs) |
| `ai_usage_state` | `ingestion-writer` | One row per `(source, actor_key, day)` — sessions, LOC, commits, PRs, cost, tokens; `actor_key` is per-tool (email for Claude Code, GitHub login for Copilot) — **not** assumed to be the same person across tools |
| `connector_activity` | `ingestion-writer` | One row per source, `last_checked_at` — advances on **every** event processed including exact duplicates, so it answers "did we hear from this source at all" independent of `raw_event`'s "did anything actually change" |

### 4.2 `core` — normalized entities, identity, RBAC

| Table | Purpose |
|---|---|
| `app_user` | Login identity: email, role (CHECK-constrained to the five BRD roles), `team_id` (pins a MANAGER's scope server-side), `github_login` (links to Personal Activity's self-scoping), `last_login_at` |
| `contributor` / `contributor_alias` | Canonical person + one alias row per per-tool identity (GitHub user, GitLab user, Jira account, git commit signature), each alias carrying a `match_confidence` and whether it was manually confirmed — never silently merged without an exact-email match |
| `team` / `team_repo` / `team_member` | Team structure (manual or imported from a GitHub org / GitLab group), which repos roll up to which team, which contributors belong to which team |
| `audit_log` | Append-only: actor (user id and/or email), action, target type/id, before/after state, source IP, timestamp. No `UPDATE`/`DELETE` is ever issued against this table by any code path. |

### 4.3 `mart` — pre-aggregated, dashboard-facing

| Table | Purpose |
|---|---|
| `metric_daily` | One row per `(metric_key, scope_id, scope_type, day)` — `scope_type` is `repo`/`org`/`team`, `scope_id` is the repo full name / `*` / a team UUID. Carries `value`, `sample_size`, and `metric_logic_version` so a historical figure is always attributable to the formula version that produced it. Sole writer: `metrics-engine`, and it's fully rebuildable from `staging` at any time — this table is a cache, not a source of truth. |

### 4.4 Why this three-schema, typed-projection shape

`raw_event` alone would make every dashboard query re-derive "latest state per entity" from JSONB
via `DISTINCT ON` + a full sort, on every request — this was measured as a real performance
problem early on and is why the typed projection tables exist at all: `ingestion-writer`
maintains them incrementally as events land, so `metrics-engine` and `api-core` read simple
indexed columns instead. The projections are still fully rebuildable from `raw_event` (several
migrations include a one-time backfill `UPDATE ... FROM (SELECT DISTINCT ON ...)` populating a
new column for pre-existing rows) — nothing here can be "wrong" in a way `raw_event` couldn't
recompute.

## 5. API surface (api-core)

Base path `/api/v1`. Auth: `Authorization: Bearer <JWT>` except the dev-token issuer itself.
Every endpoint below is the **actual implemented controller**, not the OpenAPI spec verbatim —
the spec (`src/main/resources/openapi/api-core.yml`) is contract-first for new endpoints going
forward but has drifted behind a few of the ones below; treat this table and the controllers as
ground truth, and the spec as due for a resync pass.

| Method & path | Auth | Purpose |
|---|---|---|
| `POST /auth/dev-token?email=&role=` | public, gated by `AUTH_DEV_TOKEN_ENABLED` | Issues a 15-minute RS256 JWT (ADR-0004 pilot bridge) |
| `GET /metrics/cockpit?days=&scope=` | analytical roles | DORA + PR tiles, `scope` = repo / `*` / team UUID |
| `GET /metrics/code-review?days=&scope=&repo=&sortBy=&sortDir=&page=&pageSize=` | analytical roles | Cycle-stage breakdown, reviewer load, paged aging-PR worklist |
| `GET /metrics/investment-profile?days=&scope=` | analytical roles | Planned/Unplanned/Rework/Unclassifiable breakdown |
| `GET /metrics/jira-work-items?days=&project=&q=&sortBy=&sortDir=&page=&pageSize=` | analytical roles | Backlog composition (status/type/priority/assignee/label breakdowns, overdue count), window-scoped resolution metrics, paged open-issue worklist; `project` = Jira project key or `*` |
| `GET /metrics/ai-cost-track?days=` | analytical roles | AI-01..AI-05 |
| `GET /personal/activity` | IC, self only | Opt-in personal trend |
| `GET /teams` | analytical roles | Team picker list |
| `GET /teams/repos` | analytical roles | Repo picker list (GitHub + GitLab), same scope-pinning as `/teams` |
| `GET /setup/status` | ADMIN, ENG_LEADER | Onboarding checklist + time-to-value |
| `GET /audit?limit=` | ADMIN | Audit log, newest first |
| `GET /admin/connectors` | ADMIN | Per-source health (status/last-checked/last-data-change/event count) |
| `POST /admin/connectors/repos` | ADMIN | Connect a GitHub repo (async backfill trigger) |
| `GET /admin/connectors/repos` | ADMIN | Sync status per connected repo |
| `DELETE /admin/connectors/repos?repo=` | ADMIN | Disconnect a repo (deletes its projection rows; `raw_event` untouched) |
| `POST /admin/connectors/github-teams` | ADMIN | Import a GitHub org's teams |
| `POST /admin/connectors/gitlab-projects` | ADMIN | Connect a GitLab project |
| `POST /admin/connectors/gitlab-groups` | ADMIN | Import a GitLab group |
| `POST /admin/teams`, `POST/DELETE/GET /admin/teams/{id}/repos`, `DELETE /admin/teams/{id}` | ADMIN | Team CRUD |
| `POST/GET/DELETE /admin/connectors/jira-projects` | ADMIN | Connect/list-sync-status/disconnect a Jira project — same trigger-and-poll shape as `/admin/connectors/repos`, minus team assignment |
| `POST/GET/DELETE /admin/connectors/jenkins-jobs` | ADMIN | Same trio for a Jenkins job |
| `GET/POST /admin/users` | ADMIN | User role/team assignment |
| `GET /actuator/health` | public | Liveness/readiness, every service |

Every connector additionally exposes its own small internal surface (`POST /internal/backfill`,
`GET /actuator/health`, and connector-specific webhook receivers) — see each connector's README
or the deployment guide's service table for the exact shape per connector.

## 6. Algorithms — how the headline numbers are actually computed

Full formulas, edge cases, and BRD traceability live in
[metric-definitions.md](../01-product/metric-definitions.md); this section is the "how it's
implemented" summary.

- **Deployment frequency:** `count(successful production deployment events)` from
  `workflow_run_state` where `conclusion = 'success'` and the run's branch/ref/name matches the
  configured deploy pattern (`METRICS_DEPLOY_WORKFLOW_PATTERN`), grouped by day/scope.
- **Lead time for changes:** for each merged PR/MR, `first successful deploy at/after merge −
  PR/MR opened at`; median (`percentile_cont(0.5)`) over the window. Deliberately **not** mean —
  outlier-dominated data makes a mean misleading for this metric.
- **Change failure rate:** a deployment counts as failed if, within 48 hours, a deployment whose
  branch/ref matches the hotfix pattern (`METRICS_HOTFIX_WORKFLOW_PATTERN`) lands on the same
  repo. The remediating deploy itself is excluded from the failure numerator (it's the fix, not
  the failure).
- **MTTR:** `remediation deploy finished_at − failed deploy finished_at` for deploys flagged
  failed by the change-failure-rate rule above.
- **Team-scope percentiles:** always computed from the raw per-event rows joined to
  `core.team_repo` for that team, via `percentile_cont` in SQL — **never** by averaging each
  repo's own already-computed median. This is enforced by test coverage in `metrics-engine`
  specifically because averaging medians silently produces a wrong number with no error.
- **Code Review cycle stages:** three `percentile_cont(0.5)` computations in one SQL pass over
  `pull_request_state` joined to `pull_request_review_state` — first-review time, first-approval
  time, and merge time per PR/MR, each stage's duration the difference between consecutive
  milestones.
- **Investment Profile classification:** a regex extracts a Jira-shaped issue key
  (`[A-Z]+-\d+`) from a PR/MR's title, joined against `jira_issue_state.issue_key`; the joined
  issue's type and `reopened` flag decide Planned vs. Rework vs. Unplanned. No match →
  `Unclassifiable`, never a guess.
- **AI attribution:** a PR/MR's title + body + labels are matched against a regex covering known
  AI co-author trailer conventions (`Co-authored-by:\s*(claude|copilot|cursor)`, `Generated
  (with|by)\s*(claude( code)?|copilot|cursor)`, and hyphenated `-assisted` variants) —
  case-insensitive, checked once at ingestion time and stored as a boolean column
  (`pull_request_state.ai_assisted`) rather than recomputed per query.
- **AI-05 ROI:** `estimated_hours_saved = ai_assisted_pr_count × (non_ai_cycle_time_p50 −
  ai_assisted_cycle_time_p50)`; `dollar_value_recovered = estimated_hours_saved ×
  blended_hourly_rate`; `roi_multiple = dollar_value_recovered / total_ai_spend`. Returns `null`
  (not zero) when either PR bucket has fewer than 3 merged PRs in the window.
- **Jira median resolution time / reopen rate:** `percentile_cont(0.5)` over
  `EXTRACT(EPOCH FROM (resolved_at − created_at))` for issues resolved in the window; reopen rate
  = `count(reopened AND resolved in window) / count(resolved in window) × 100`. Both `null` (not
  zero) when nothing resolved in the window — a true `0%` reopen rate on zero resolutions would be
  a fabricated number, not an honest one.
- **Jira open backlog vs. pipeline shape:** "open" is `resolved_at IS NULL`, unwindowed by design
  — an old open issue is still real backlog and must not disappear from type/priority/assignee/
  label breakdowns just because it predates the trailing window. `statusBreakdown` is the one
  exception: it includes resolved issues too (windowed by `created_at`), since it exists
  specifically to show the full To Do/In Progress/Done shape of the pipeline, not just what's
  still open.

## 7. Security architecture

- **Authentication:** `api-core` is a Spring Security OAuth2 resource server validating RS256
  JWTs. In the current pilot phase, tokens are self-issued via the gated `/auth/dev-token`
  bridge (ADR-0004) using an RSA keypair generated at boot (ephemeral — restarts invalidate every
  outstanding token, which is acceptable pre-production). **Before any production deployment,
  `AUTH_DEV_TOKEN_ENABLED` must be `false`** and a real OIDC/SSO provider's JWKS wired in instead
  — this is a tracked, explicit gap, not an oversight.
- **Authorization:** enforced in the `SecurityFilterChain`, not in the frontend — `/metrics/**`
  and `/teams/**` require an analytical role; `/audit/**` and `/admin/**` require `ADMIN`;
  `/personal/**` requires `IC` and is further self-scoped in the query layer (a caller can only
  ever see their own `github_login`'s activity, resolved server-side from the JWT's email, not
  from a client-supplied identifier). A `MANAGER`'s `scope=*` request is silently resolved down
  to their own `team_id` — the client cannot request a different team's data by passing a
  different scope value.
- **Connector credentials:** every external API token (`GITHUB_TOKEN`, `GITLAB_TOKEN`,
  `JIRA_API_TOKEN`, `JENKINS_API_TOKEN`) is a plain environment variable today — scoped as
  narrowly as the vendor allows (e.g. GitLab's token is `read_api`/Reporter, never write). No
  secrets-manager integration exists yet; see the deployment guide's production-hardening list.
- **Webhook verification:** GitHub HMAC-SHA256 (`X-Hub-Signature-256`, constant-time compare);
  GitLab and Jira use a shared-secret token instead (GitLab doesn't HMAC-sign; Jira sends neither
  a signature nor a delivery GUID, so its idempotency key is a SHA-256 hash of the raw body
  instead). All three fail closed — an unconfigured secret rejects every webhook rather than
  accepting unauthenticated ones.
- **Audit log:** append-only, `ADMIN`-only read, records every config change / connect /
  disconnect / role assignment with actor, before/after state, and source IP.

## 8. Resilience & failure handling

- **Vendor outage / rate limit:** a connector detects a 429/exhausted rate limit and returns it
  to the caller distinctly (e.g. `{"error":"github_rate_limited","retryAfter":...}`) rather than
  a generic 500 — this was a real production bug (an opaque 500 was indistinguishable from an
  actual defect) fixed by making the failure mode explicit end to end, surfaced in the Admin
  console's sync-status table as "Failed" with a human-readable reason and a Refresh action.
- **Queue consumer failure:** a message `ingestion-writer` can't process (malformed JSON, a
  genuinely broken payload) is rejected **without requeue**, routing it to a dead-letter queue
  (`staging.events.dlq`) rather than retrying forever or silently dropping it.
- **Idempotent everything:** every insert/upsert in `ingestion-writer` is safe to run twice — a
  queue redelivery, a re-run backfill, or a scheduled auto-refresh cycle that finds nothing new
  all produce identical end state, never a duplicate row or a corrupted aggregate.
- **Connector isolation:** a connector process crashing or a vendor being fully unreachable
  cannot corrupt or block any other service — its only interface to the rest of the system is
  "publish to a queue," and RabbitMQ buffers indefinitely (bounded by broker storage) until the
  connector recovers.

## 9. Testing strategy

| Layer | Approach |
|---|---|
| Metric formulas (`metrics-engine`) | Testcontainers-backed integration tests against real Postgres — deployment-rule matching, dedup, percentile correctness, team-vs-repo-median distinction explicitly tested (see §6) |
| Ingestion idempotency (`ingestion-writer`) | Testcontainers integration test proving duplicate inserts are no-ops against real Postgres |
| Webhook verification (each connector) | Unit tests: valid signature/token, wrong secret, tampered body, missing header, unconfigured-secret fail-closed |
| Identity resolution | Pure-logic unit tests: alias dedup, confidence-scored email merge, no-silent-merge guarantee, bot detection |
| Frontend | `tsc -b` (strict mode) + `vite build` must pass before merge; ESLint config pending |
| End-to-end / smoke | `infra/smoke-e2e.sh` exercises the real pipeline via the Docker CLI, covering paths Testcontainers can't reach in every local Docker configuration (e.g. Docker Desktop's Enhanced Container Isolation blocking Testcontainers' socket access) |

**Known test gaps, disclosed rather than hidden:** `connector-jenkins` and
`connector-ai-telemetry` have no automated test suite yet; `StagingEventWriter`'s Jenkins- and
GitLab-approval-handling paths aren't yet covered by `ingestion-writer`'s integration test. Each
gap is called out in its respective service's README rather than left undocumented.

## 10. CI/CD and local development

- **Local dev:** `infra/start-backend.sh` builds the full Maven reactor once, then starts eight
  of the nine backend services as plain `java -jar` processes with health-check polling;
  `connector-gitlab` is started separately via `docker compose -f infra/docker-compose.yml up
  --build gitlab` since it's the one containerized service. `infra/docker-compose.yml` also
  starts Postgres and RabbitMQ for local dev.
- **Contract-first API changes:** the OpenAPI spec is meant to be updated before implementing an
  endpoint change (engineering standards §5); §5 above notes where this has drifted in practice —
  treat that as a backlog item, not a template to repeat.
- **No CI pipeline is defined in this repository yet** (no `.github/workflows/`) — tests are run
  locally today; a real deployment should add one before relying on this as a release gate.
