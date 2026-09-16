# AI Impact Evaluation — Project Status Handoff

**Last updated:** 2026-09-16. This file primes a fresh terminal session with current project
state — not a pending implementation brief right now (the previous version of this file, an
implementation brief for the Jenkins CI/CD connector, described work that has since **shipped**
and is long since built; its content is gone from this file for that reason — see git log /
`docs/CHANGELOG.md` if the history is needed).

## What's actually built and live, as of this update

- **Connectors:** GitHub, GitLab (containerized, ADR-0005), Jira, Jenkins, AI Telemetry (Claude
  Code + Copilot usage-report ingestion) — all publishing into the shared
  `connector-* → RabbitMQ → ingestion-writer → staging.* → api-core → frontend` pipeline.
- **Dashboards, all live (not mock) except where noted:** Cockpit (DORA, org/team/repo scope),
  Teams (org→team, org→repo, GitHub+GitLab parity), Code Review Analytics (cycle stages, reviewer
  load, aging PRs — GitHub+GitLab parity), Investment Profile (Planned/Unplanned/Rework via
  Jira↔PR linkage), **Jira Work Items** (new — see below), AI Cost Track (AI-01..AI-05; badged
  "Demo data · real API schema" since the usage export is still a sample file shaped like the real
  vendor API, not a genuine enterprise export), Personal Activity (opt-in, IC-only), Setup/
  Onboarding (time-to-value), Admin Console (connector health, repo/team connect+sync+delete, user
  admin, audit log).
- **RBAC:** five roles (ADMIN, ENG_LEADER, MANAGER, IC, FINANCE_READONLY), server-enforced
  (ADR-0004), role-aware nav (`frontend/src/roleAccess.ts`).

## Just shipped this session (2026-09-16) — Jira Work Items dashboard

New Jira-specific dashboard (`frontend/src/views/Jira.tsx`, `GET
/api/v1/metrics/jira-work-items`, `services/api-core/.../jira/` package): open/resolved/overdue
KPIs, median resolution time, reopen rate, a To Do/In Progress/Done pipeline-shape chart,
open-backlog breakdowns by type/priority/assignee, a "topics" view of the most common Jira labels,
a weekly resolution-time trend, and a searchable/sortable/paged open-issue worklist. Backed by a
`V14` migration widening `staging.jira_issue_state` with `priority`, `status_category`,
`reporter`, `labels[]`, `due_date` — **standard Jira fields only**, never an instance-specific
`customfield_XXXXX` guess (story points/epic link are deliberately unsupported for that reason,
per the no-fabrication policy — see `docs/01-product/metric-definitions.md`'s "Jira Work Items"
section for the full reasoning).

**State:** committed on `feat/connector-gitlab` (3 commits on top of `origin/main` as of this
writing — the branch name predates this work and is stale relative to its actual content), pushed
to `origin`, PR opened against `main` (not yet merged as of this update — check GitHub for current
status: `gh pr status` or the repo's Pull Requests tab).

**Verification performed (real, not just compiled):** `mvn test` clean across `api-core`/
`ingestion-writer` (22/22 api-core tests; also fixed a pre-existing broken `CockpitSecurityTest` —
missing `ScopeResolver` stub bean — found while running the suite, unrelated to Jira otherwise).
Frontend `tsc`/`vite build` clean. Live-verified against a **real** local Postgres
(`infra/docker-compose.yml`): `V14` applied cleanly, queried against real pre-existing connected
Jira data (a "SCRUM" project) plus synthetic seed rows for edge cases, RBAC checked via dev-tokens
(ADMIN/MANAGER/FINANCE_READONLY → 200, IC → 403), and the actual frontend driven in a browser
end-to-end under ADMIN and MANAGER logins — every chart, the topics pills, search, project filter,
and empty/null states (e.g. "n/a" resolution time rather than a fabricated 0%) confirmed correct.

## What's NOT queued as a next task

No specific next task is pending from the user as of this update. If a fresh session reads this
file expecting one, don't assume the Jira Work Items work above is still "in progress" — it
shipped, per the state described. For what's actually next, check (in order): the PRD's delivery
status (`docs/01-product/prd.md` Appendix B — `⬜`/`🟡` rows), `docs/CHANGELOG.md`'s most recent
entries, or just ask the person directly.

## Known standing gaps (not this session's doing, still true)

- OpenAPI spec (`services/api-core/src/main/resources/openapi/api-core.yml`) has historically
  drifted behind a few shipped endpoints — `docs/03-architecture/technical-specification.md` §5 is
  the more reliable "actual implemented controller" reference; treat it as ground truth over the
  spec where they disagree.
- SonarQube and PagerDuty/Opsgenie connectors are named in the BRD but not yet built (Phase 2).
- Sub-team hierarchy (`parent_team_id`) exists in the schema but nothing populates or renders it.
- Cross-tool AI-assistant identity resolution doesn't exist yet — an engineer using both Claude
  Code and Copilot counts as two distinct "active AI users."
