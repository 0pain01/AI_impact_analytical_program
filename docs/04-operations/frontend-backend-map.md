# Frontend ↔ Backend Map — every screen, every API call, every downstream effect

This is the one document that answers "what happens when I click that" and its reverse, "what UI
feature does this endpoint/table/queue actually serve" — a SWE, a PM, or a business owner should
be able to find their answer here without reading source. It complements, never replaces:
- [`deployment-guide.md`](deployment-guide.md) — what each service *is*, why it's separate, ports,
  containerization, and the full credential/env-var reference.
- [`../03-architecture/technical-specification.md`](../03-architecture/technical-specification.md) —
  the formal API surface, data model, and algorithm-level "how metrics are computed."
- [`../01-product/functional-specification.md`](../01-product/functional-specification.md) — what
  each dashboard is *for*, in product terms, and the explicit out-of-scope list.

If this doc and the source code disagree, the code is right — file an issue against this doc.

---

## 0. The one-paragraph mental model

Every screen in the frontend calls **only `api-core`** (`http://localhost:8080` in dev), never a
connector or another service directly. Read-only screens (Cockpit, Code Review, Jira Work Items,
AI Cost Track, Investment Profile, Personal, Teams, Setup) query Postgres through `api-core`,
already-computed by `metrics-engine` or `ingestion-writer` ahead of time. The one write-shaped
screen — Admin — either writes directly to Postgres (teams, users, disconnect) or makes `api-core`
call *into* a connector's `POST /internal/backfill` endpoint (connect/refresh), which is the one
deliberate exception to "connectors never get called into" (see `deployment-guide.md` §3). Data
that connectors *emit* takes a different path entirely and never touches `api-core` on the way
in: `connector → RabbitMQ → ingestion-writer → staging tables`, fully decoupled from any HTTP
request a user makes.

---

## 1. Login / session

| Action | Call | Backend | Effect |
|---|---|---|---|
| Enter email, click Continue | `POST /api/v1/auth/dev-token` | `DevTokenController` → `DevTokenService` | Looks up `core.app_user` by email (first-ever login on an empty table auto-bootstraps that email as ADMIN); issues an RS256 JWT with `role`/`scope` claims; writes an `AUTH_TOKEN_ISSUED` audit row. No password — this is the dev/pilot bridge (ADR-0004), not final auth. |
| Every subsequent request | `Authorization: Bearer <jwt>` header, added by `authFetch()` in `api.ts` | `SecurityConfig` (Spring Security JWT resource server) | Verifies the JWT, extracts `role` claim as the Spring Security authority; `SecurityConfig`'s route rules (see §9 below) then gate or reject the request server-side — the frontend hiding a tab is convenience, not the actual enforcement. |
| Log out | Local only — clears `sessionStorage`, no backend call | — | — |

## 2. Cockpit

| Action | Call | Backend | Reads |
|---|---|---|---|
| Load tab / change 30↔90 days / pick a team or repo | `GET /api/v1/metrics/cockpit?days=&scope=` | `CockpitController` → `ScopeResolver` (pins MANAGER to their team server-side) → `CockpitQueryService` | `mart.metric_daily` — pre-aggregated by `metrics-engine`, not computed on request |
| "Export report" | Client-side only (builds a CSV from data already on screen) | — | — |
| Teams tab → "Repositories" per-repo drill-down | `GET /api/v1/teams/repos` then `GET /api/v1/metrics/cockpit?scope=<repo>` | `TeamController` → `TeamQueryService`, then Cockpit as above | `staging.pull_request_state`/`workflow_run_state` distinct repos, then `mart.metric_daily` |

## 3. Teams

| Action | Call | Backend | Reads/Writes |
|---|---|---|---|
| Load tab | `GET /api/v1/teams` | `TeamController` → `TeamQueryService` | `core.team` + repo counts from `core.team_repo` |
| Click a team | Drives Cockpit's `scope=<teamId>` (see §2) | — | — |

## 4. Investment Profile

| Action | Call | Backend | Reads |
|---|---|---|---|
| Load tab / pick team or repo / change window | `GET /api/v1/metrics/investment-profile?days=&scope=` | `InvestmentProfileController` → `ScopeResolver` → `InvestmentProfileQueryService` | Joins `staging.pull_request_state` (regex-extracts a `[A-Z]+-\d+` issue key from the PR title) against `staging.jira_issue_state`; no match → `Unclassifiable`, never guessed |

## 5. Code Review Analytics

| Action | Call | Backend | Reads |
|---|---|---|---|
| Load tab / change window / filter by repo / sort / page | `GET /api/v1/metrics/code-review?days=&scope=&repo=&sortBy=&sortDir=&page=&pageSize=` | `CodeReviewController` → `ScopeResolver` → `CodeReviewQueryService` | `staging.pull_request_state` + `staging.pull_request_review_state` directly (no `mart` rollup for this tab yet) |

## 6. Jira Work Items

| Action | Call | Backend | Reads |
|---|---|---|---|
| Load tab / change window / filter by project / search / sort / page | `GET /api/v1/metrics/jira-work-items?days=&project=&q=&sortBy=&sortDir=&page=&pageSize=` | `JiraDashboardController` → `JiraDashboardQueryService` | `staging.jira_issue_state` directly. `openIssues`/backlog breakdowns/worklist are unwindowed (current state); resolution-time KPIs and the trend chart are windowed by `days`; `statusBreakdown` is windowed but not filtered to open issues — see the endpoint's own doc comment for why |

## 7. AI Cost Track

| Action | Call | Backend | Reads |
|---|---|---|---|
| Load tab / change window | `GET /api/v1/metrics/ai-cost-track?days=` | `AiCostTrackController` → `AiCostTrackQueryService` | `staging.ai_usage_state` (spend/adoption) + `staging.pull_request_state.ai_assisted` (impact/ROI, segmented by AI-attribution). `impact`/`roi` come back `null`, not a guess, below a 3-merged-PR sample floor per bucket |

## 8. Personal Activity (IC-only, opt-in)

| Action | Call | Backend | Reads |
|---|---|---|---|
| Load tab | `GET /api/v1/personal/activity` | `PersonalController` → `PersonalQueryService` | Self-scoped via the caller's own `core.app_user.github_login` — no `scope` param exists on this route, by design (BRD rule 1: no org/team surveillance surface for an IC) |

## 9. Setup / Onboarding

| Action | Call | Backend | Reads |
|---|---|---|---|
| Load tab (ADMIN/ENG_LEADER only) | `GET /api/v1/setup/status` | `SetupController` → `SetupQueryService` | Derives the 4-item checklist and time-to-value entirely from existing `staging.raw_event`/`mart.metric_daily` timestamps — no manually-maintained flag anywhere |

## 10. Admin Console

### 10a. Connectors panel (health table)

| Action | Call | Backend | Reads |
|---|---|---|---|
| Load panel | `GET /api/v1/admin/connectors` | `AdminController` → `AdminConnectorService` | `staging.connector_activity` (last-checked) + `staging.raw_event` (last-data-change, event count), per source |

### 10b. Repos & Teams (GitHub/GitLab)

| Action | Call | Backend does | Downstream (async, after the HTTP response) |
|---|---|---|---|
| "Connect repo" | `POST /api/v1/admin/connectors/repos` `{owner, repo, teamId}` | `ConnectorAdminController` → `ConnectorAdminService.connectRepo()` — optionally maps repo→team immediately, writes `REPO_CONNECT_TRIGGERED` audit row, returns 202 | Calls **connector-github**'s `POST /internal/backfill?owner=&repo=` → connector publishes one `EventEnvelope` per PR/commit/workflow-run to RabbitMQ (`aiimpacteval.events` exchange) → **ingestion-writer** (queue `staging.events`, binding `#`) writes `staging.raw_event` + upserts `pull_request_state`/`workflow_run_state`/`pull_request_review_state` → **identity-service** (queue `identity.events`, same exchange) resolves contributors → `core.*` → **metrics-engine** picks up the new staged rows on its next 5-min recompute cycle → `mart.metric_daily` → Cockpit/Code Review reflect it |
| "Connect GitLab project" | `POST /api/v1/admin/connectors/gitlab-projects` `{project, teamId}` | `ConnectorAdminService.connectGitlabProject()`, tracked key `"gitlab:" + project` | Same shape, against **connector-gitlab**'s `POST /internal/backfill?project=`; rows land `gitlab:`-prefixed in the same shared tables |
| "Import org teams" (GitHub) | `POST /api/v1/admin/connectors/github-teams` `{org}` | `ConnectorAdminService.connectGithubOrgTeams()` | connector-github's `POST /internal/backfill-teams?org=` → publishes `team.snapshot` → identity-service → `core.team`/`team_repo`/`team_member` |
| "Import group" (GitLab) | `POST /api/v1/admin/connectors/gitlab-groups` `{group}` | `ConnectorAdminService.connectGitlabGroup()` | Same, against connector-gitlab's `POST /internal/backfill-groups?group=` |
| Sync status table | `GET /api/v1/admin/connectors/repos` | `ConnectorAdminService.listRepoSyncStatus()` | Reads `staging.pull_request_state`/`workflow_run_state` (last sync, event count) + an in-memory trigger map for IN_PROGRESS/FAILED state (not persisted — restarting api-core loses "currently syncing," not the underlying data) |
| "Refresh" (a row) | Same as Connect, re-triggered | — | — |
| "Delete" (a row) | `DELETE /api/v1/admin/connectors/repos?repo=` | `ConnectorAdminService.disconnectRepo()` | Deletes that repo's rows from the three projection tables + `core.team_repo` directly — **no connector call**, and `staging.raw_event` is untouched (see §11's caveat on reconnecting) |

### 10c. Jira & Jenkins

Identical shape to 10b, minus team assignment (no project/job→team mapping exists):

| Action | Call | Backend | Downstream |
|---|---|---|---|
| "Connect project" (Jira) | `POST /api/v1/admin/connectors/jira-projects` `{projectKey}` | `.connectJiraProject()` | connector-jira's `POST /internal/backfill?projectKey=` → `issue.snapshot` events → ingestion-writer → `staging.jira_issue_state` |
| "Connect job" (Jenkins) | `POST /api/v1/admin/connectors/jenkins-jobs` `{jobName}` | `.connectJenkinsJob()` | connector-jenkins's `POST /internal/backfill?jobName=` → `build.snapshot` events → ingestion-writer → `staging.workflow_run_state` (`jenkins:`-prefixed `run_id`, conclusions normalized to lowercase) |
| Sync status tables | `GET .../jira-projects`, `GET .../jenkins-jobs` | `.listJiraProjectSyncStatus()`, `.listJenkinsJobSyncStatus()` | `staging.jira_issue_state` / `staging.workflow_run_state` (scoped to `jenkins:`-prefixed rows) |
| "Delete" | `DELETE .../jira-projects?projectKey=`, `DELETE .../jenkins-jobs?jobName=` | `.disconnectJiraProject()`, `.disconnectJenkinsJob()` | Deletes that project's/job's rows only — same untouched-`raw_event` caveat as 10b |

### 10d. Users & role assignments (RBAC)

| Action | Call | Backend | Writes |
|---|---|---|---|
| Add user | `POST /api/v1/admin/users` | `AdminUserController` | `core.app_user` |
| Change role/team | `PATCH /api/v1/admin/users/{id}/role` | `AdminUserController` | `core.app_user.role`/`team_id` — this is what `ScopeResolver` actually enforces against on every subsequent request from that user |
| Set GitHub login | `PATCH /api/v1/admin/users/{id}/github-login` | `AdminUserController` | `core.app_user.github_login` — what Personal Activity's self-scoping resolves against |
| Deactivate/reactivate | `PATCH /api/v1/admin/users/{id}/active` | `AdminUserController` | `core.app_user.active` |

### 10e. Team management

| Action | Call | Backend | Writes |
|---|---|---|---|
| Create/rename team | `POST /api/v1/admin/teams` | `TeamAdminController` | `core.team` |
| Assign/unassign repo | `POST`/`DELETE /api/v1/admin/teams/{id}/repos` | `TeamAdminController` | `core.team_repo` |
| Delete team | `DELETE /api/v1/admin/teams/{id}` | `TeamAdminController` | Cascades `team_repo`/`team_member`; 409 if a user is still pinned to it via `team_id` — reassign first |

### 10f. Audit log

| Action | Call | Backend | Reads |
|---|---|---|---|
| Load panel | `GET /api/v1/audit?limit=` | `AuditController` → `AuditQueryService` | `core.audit_log` — append-only, every write action above lands a row here |

---

## 11. The one thing that looks wrong but is documented, not a bug (yet)

Deleting a repo/project/job and immediately reconnecting it **does not reliably restore the
data** if nothing changed upstream since the last sync — every connector republishes events under
a deterministic idempotency key (`pr:{id}:{updatedAt}`, `issue:{id}:{updated}`,
`jenkins:{job}:{buildNumber}`), and `staging.raw_event`'s own uniqueness constraint silently
deduplicates a republish with an unchanged key before the projection is ever rebuilt. Confirmed
against GitHub, GitLab, Jira, and Jenkins alike — this isn't a Jira/Jenkins-specific gap. See the
real Jira ticket **SCRUM-11** or `docs/CHANGELOG.md`'s 2026-09-17 entry for the full root cause;
the fix needs an ADR-level decision (does Delete also purge `raw_event`? does backfill get a
force-republish bypass?), not a quick patch.

---

## 12. Docker ↔ backend wiring

Two different worlds, and it matters which one a given service is in:

- **Infra + 2 connectors, containerized** (`infra/docker-compose.yml`): `postgres`, `rabbitmq`,
  `gitlab` (connector), `jenkins` (the real CI server, not our code — infra, like Postgres),
  `connector-jenkins`. These reach each other by **compose service name** on the compose network
  (e.g. `connector-jenkins`'s `JENKINS_BASE_URL=http://jenkins:8080`, not `localhost:9090` — that
  host-facing form only works for a process running directly on the host).
- **Everything else, plain processes**: `api-core`, `metrics-engine`, `identity-service`,
  `ingestion-writer`, `connector-github`, `connector-jira`, `connector-ai-telemetry`, and the
  frontend dev server. These reach Postgres/RabbitMQ over `localhost` (the compose services'
  *host*-published ports: `5442` for Postgres, `5672`/`25672` for RabbitMQ) and reach each other
  (e.g. `api-core` → a connector's `/internal/backfill`) also over `localhost`, via each
  service's own published port (`8080`–`8088`, see `deployment-guide.md` §4 for the exact map).
- **The gotcha:** a plain process started with `mvn spring-boot:run` does **not** get
  `infra/.env` automatically the way `docker compose` does — source it into your shell first
  (`set -a; source infra/.env; set +a`) or that connector runs unauthenticated/misconfigured with
  no error at startup.
- **Building the images yourself:** `docker build -f services/connectors/connector-gitlab/Dockerfile -t connector-gitlab .`
  (build context is `services/`, not the connector's own directory — the image needs the parent
  POM + `platform-common`) — same pattern for `connector-jenkins`. Or just
  `docker compose up -d --build gitlab jenkins connector-jenkins` and let compose do it.

## 13. Business/PM quick index

Not duplicated here — these are genuinely better answered from their own source, not summarized:

| Question | Where |
|---|---|
| What does this product do, who's it for, what are the five roles | `functional-specification.md` §1–3 |
| What's actually shipped vs. planned, epic by epic | `prd.md` Appendix B (delivery status) |
| What's the formula/definition behind a specific metric | `metric-definitions.md` |
| What's explicitly out of scope / not built | `functional-specification.md` §6 |
| Non-negotiable product rules (no surveillance, no manual tagging, etc.) | `functional-specification.md` §2 / root `CLAUDE.md` |
| Is this production-ready / SOC2-ready | `deployment-guide.md` §7.4 ("Production hardening not yet built") — answer honestly, it isn't yet, and that's a named/deliberate gap list, not an oversight |
| What credentials does someone need to run this | `deployment-guide.md` §7.1 and §7.3 |
