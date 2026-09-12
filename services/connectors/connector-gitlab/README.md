# connector-gitlab

Ingestion-layer connector for GitLab (PRD F1, FR-1.1). Publishes raw GitLab data to the
`aiimpacteval.events` exchange per ADR-0003. Owns **no business logic** (ADR-0002).

**Status:** webhook path + merge-request/commit/pipeline backfill implemented, and wired end to
end — merge requests and pipelines land in the same `pull_request_state`/`workflow_run_state`
tables GitHub/Jenkins share (via `ingestion-writer`'s `StagingEventWriter`, `repo` values
prefixed `gitlab:`), feed DORA/PR-velocity/cycle-time in `metrics-engine` with zero
GitLab-specific query changes, and are connectable/visible from the Admin console
(`POST /admin/connectors/gitlab-projects`, `/gitlab-groups`; health cards `gitlab`/`gitlab_ci`).
GitLab CI/CD pipeline data (PRD F3, FR-1.3) flows through this connector — live via the generic
webhook (`pipeline` object_kind), history via backfill. Group-structure import (PRD E2-S2,
FR-1.5) fetches a group's projects (incl. subgroups) + members and publishes one snapshot per
group for the identity service to normalize into `core.team`, same as a GitHub org's teams.
Merge-request **approvals** (`GET /merge_requests/:iid/approvals`, one extra call per MR during
backfill — same N+1 shape connector-github's PR-review fetch already uses) land in the same
`pull_request_review_state` table GitHub PR reviews use, so Code Review Analytics' cycle-stage
breakdown and reviewer-load leaderboard work for GitLab the same way they do for GitHub —
verified against a real approval on a real GitLab.com project, not assumed from docs.

**Known fidelity gaps:** GitHub Actions workflow runs and Jenkins jobs both carry a **name** the
deploy/hotfix detection pattern matches against; GitLab pipelines don't — the closest available
field is the pipeline's **git ref**. Set `METRICS_DEPLOY_WORKFLOW_PATTERN`/
`METRICS_HOTFIX_WORKFLOW_PATTERN` (metrics-engine) to include your deploy branch (e.g.
`main|production`) or GitLab deployments won't be detected at all — see
metric-definitions.md's GitLab section. GitLab's approval model also has no equivalent to
GitHub's "changes requested"/"commented" review states — only "approved, by whom, when" — so
every GitLab review row is `APPROVED`; this is a real difference between the two platforms, not
an implementation gap, and nothing here guesses a mapping for unresolved discussion threads.
Also not yet: job-level pipeline detection, adaptive rate-limit throttling, webhook-gap healing
poller.

This is currently the only connector packaged as a Docker image (ADR-0005) — the others
(`connector-github`, `connector-jira`, `connector-jenkins`, `connector-ai-telemetry`) still run
locally via `mvn spring-boot:run` per their own READMEs.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /webhooks/gitlab` | GitLab webhook receiver. Verifies the `X-Gitlab-Token` header (constant-time, plain shared-secret comparison — GitLab does not HMAC-sign payloads like GitHub); 401 on failure. |
| `POST /internal/backfill?project={id}` | Backfills merge requests, commits, and pipelines for the configured window. `project` is a numeric GitLab project ID or a `namespace/project` path — either works against this endpoint directly, though api-core's Admin console only ever sends the path form (see ConnectorAdminService's javadoc: its tracked repo key is derived from the caller's raw input, which only matches what actually lands in the state tables for the path form). Internal — invoked by api-core on project connect. Returns 429 (`{"error":"gitlab_rate_limited","retryAfter":...}`) instead of a generic 500 if GitLab's rate limit is exhausted mid-backfill — set `GITLAB_TOKEN` to raise the cap above the unauthenticated default. |
| `POST /internal/backfill-groups?group={id}` | Backfills a group's projects (including subgroups) and members. `group` is a numeric group ID or group/subgroup path. Internal — invoked by api-core on group connect. |
| `GET /actuator/health` | Liveness/readiness. |

## Event types published

`gitlab.<object_kind>` (e.g. `gitlab.merge_request`, `gitlab.push`, `gitlab.pipeline`) for live
webhooks — `object_kind` is GitLab's own event-type field on every project webhook payload;
`gitlab.merge_request.snapshot` / `gitlab.commit.snapshot` / `gitlab.pipeline.snapshot` /
`gitlab.merge_request_approval.snapshot` / `gitlab.group.snapshot` for backfill — the approval
snapshot is one event per approver per MR (`approved_by[]` from the approvals endpoint), not per
merge request. Like `team.snapshot` in connector-github,
`group.snapshot`'s sourceId includes the current instant rather than an entity timestamp
(GitLab exposes no single "updated at" for a group's membership/project set) — every run
publishes a fresh snapshot, and the identity service's own upsert logic keeps repeated imports
safe.

## Configuration (env vars)

| Var | Default | Purpose |
|---|---|---|
| `GITLAB_WEBHOOK_SECRET` | *(empty — all webhooks rejected)* | Shared secret configured on the GitLab webhook (Settings > Webhooks > Secret token) |
| `GITLAB_TOKEN` | *(empty)* | Read-only personal/project/group access token (scope: `read_api`) for backfill, sent as `PRIVATE-TOKEN` |
| `GITLAB_API_BASE_URL` | `https://gitlab.com/api/v4` | Override for self-managed GitLab instances |
| `GITLAB_BACKFILL_DAYS` | `90` | Backfill window (PRD F1) |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | localhost defaults | Queue connection |
| `SERVER_PORT` | `8088` | HTTP port (8080-8087 are already claimed by the other 8 backend services — see `infra/start-backend.sh`) |

## Run locally (without Docker)

```
mvn -pl connectors/connector-gitlab -am spring-boot:run
```

Requires RabbitMQ reachable per the env vars above (`infra/docker-compose.yml` starts it).

## Run with Docker

Build context is the Maven reactor root (`services/`), since the image needs the parent POM
and `platform-common`:

```
docker build -f connectors/connector-gitlab/Dockerfile -t connector-gitlab .
docker run --rm -p 8088:8088 \
  -e RABBITMQ_HOST=host.docker.internal \
  -e GITLAB_WEBHOOK_SECRET=changeme \
  connector-gitlab
```

Or via compose from the repo root — this also wires it to the shared RabbitMQ:

```
docker compose -f infra/docker-compose.yml up --build gitlab
```

## Tests

`mvn test -pl connectors/connector-gitlab` — covers token verification (valid, wrong secret,
missing header, unconfigured secret) and controller publish/reject behavior with a fixed clock.
