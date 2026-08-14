# Product Requirements Document — AI Impact Evaluation (v1.0)

- **Status:** v1.0 — Draft for stakeholder review (pending sign-off)
- **Date:** 2026-07-05 · **Authors:** Vishal & Aditi
- **Source of truth:** [`AI_Impact_Evaluation_PRD_v1.0.docx`](AI_Impact_Evaluation_PRD_v1.0.docx) (this markdown mirrors it
  for day-to-day engineering use; the docx is authoritative for sign-off)
- **Upstream:** BRD v1.0 (see [brd-summary.md](brd-summary.md)). The BRD answers *why*; this PRD
  answers *what the product does, for whom, and to what acceptance standard*.
- **Downstream:** C4 architecture model ([../03-architecture/system-architecture.md](../03-architecture/system-architecture.md)),
  metric formulas ([metric-definitions.md](metric-definitions.md)), delivery backlog (Section 6 epics).
- **Delivery status against this PRD:** see [Appendix B](#appendix-b--delivery-status).

## 1. Vision & product overview

Give every engineering leader a single, trustworthy, real-time answer to: **"How healthy is our
software delivery, and what is our AI investment returning?"**

AI Impact Evaluation is a multi-tenant web platform. Teams connect their existing tools once; AI Impact Evaluation ingests,
normalizes identities and team structures, computes DORA/SPACE-aligned metrics on a materialized
semantic layer — no manual tagging, no spreadsheet rebuilds — and surfaces them as role-based
drill-down dashboards, exportable executive reports, and (Phase 3) AI ROI and automated first-pass
code review. It is an analytics layer over the toolchain, never a replacement for Jira/GitHub/GitLab.

### Product goals

| Goal | Description | BRD objective |
|---|---|---|
| PG-1 | One source of truth for delivery performance across all teams | BO-1 |
| PG-2 | Fully automated DORA + SPACE-aligned metrics, no manual status updates | BO-2 |
| PG-3 | Quantify AI coding-assistant adoption and ROI in dollars | BO-3 |
| PG-4 | Reduce code-review cycle time and defect leakage | BO-4 |
| PG-5 | Make planned-vs-unplanned engineering time visible (Investment Profile) | BO-5 |
| PG-6 | Role-based views tuned to Exec, Manager, and IC needs | BO-6 |
| PG-7 | Commercialization-ready without re-architecture (optional path) | BO-7 |

### Non-goals

Not a PM/Git-hosting replacement · no keystroke/idle-time/surveillance signals (deliberate product
boundary, not a backlog gap) · no HR performance-review documents (deferred beyond Phase 3) · never
requires engineers to change how they work.

### Guiding product principles

1. **Trust over surveillance** — individual views opt-in and growth-framed; no metric may rank or
   shame an individual by default; metric definitions always visible.
2. **Insight, not raw data** — every screen answers a question a leader actually asks, with
   context, thresholds, and trend.
3. **Zero-tagging automation** — metrics derive from tool events; garbage-in-garbage-out is
   designed out.
4. **Fast time-to-value** — first meaningful dashboard within 30 minutes; no query language for
   core use.
5. **Accessible depth** — pre-built reports serve 90% of users; a guided custom-metric builder
   serves power users without SQL literacy.
6. **Resilient by design** — a vendor API outage degrades gracefully and never loses data or
   corrupts a metric.

## 2. Positioning

The deliberate middle ground: minware's automated, no-tagging DORA/SPACE analytics delivered
through Hivel's leadership-friendly storytelling UX — explicitly avoiding surveillance (Hivel's
risk) and a mandatory query language (minware's). Built internal-first but
commercialization-ready: Phase 1 ships SaaS multi-tenant for internal pilots; the architecture
must not preclude external onboarding, VPC/on-prem ingest, or SOC 2 Type II (see OQ-1).

## 3. Personas & jobs-to-be-done

| Persona | Primary job-to-be-done | Primary surface | Access |
|---|---|---|---|
| CTO / VP Engineering | Report delivery speed, quality & AI ROI to the board with confidence | Executive Cockpit, AI ROI Summary | Org-wide |
| Engineering Manager | Find bottlenecks, balance workload, prep 1:1s from real data | Team Dashboard, Activity, Investment Profile | Team scope |
| Tech Lead / Senior Eng | Keep review load healthy and quality trends positive | Code Review Analytics | Team scope |
| Individual Contributor | See own trends transparently and non-punitively | Personal Activity (opt-in) | Self only |
| Product / Program Mgr | Predict delivery, see scope creep early | Investment Profile, Cycle-Time reports | Team scope |
| Finance / Procurement | Judge cost-benefit of AI tooling and AI Impact Evaluation itself | AI ROI Financial Report | Read-only, aggregated |
| Security / Compliance | Govern access, audit data use, enforce privacy | Admin & Access Console | Admin |

Success moments (the moments that define product-market fit per persona): Exec exports a
board-ready report with RAG DORA status and a dollar AI-ROI figure in under two minutes · Manager
finds the sprint's real bottleneck and drills to the stalled PRs without anyone being ranked ·
Tech Lead gets flagged on an aging oversized PR before it blocks release · IC opts in, sees clear
definitions, trusts nobody is watching keystrokes · PM sees unplanned work rising before the
milestone slips · Finance sees cost/quality/throughput deltas attributed to specific AI tooling ·
Compliance retrieves a 12-month audit log on demand.

## 4. Success metrics & instrumentation

**North Star: weekly leaders acting on AI Impact Evaluation insight** — count of Exec/Manager users taking a
tracked action (drill-down, export, goal edit, follow-up on a flagged PR) at least once a week.

| KPI | Target | How measured |
|---|---|---|
| Time-to-first-insight | < 30 min from first tool connection | `connector_connected` → `first_dashboard_rendered` |
| Manual reporting effort saved | 70%+ reduction within 2 quarters | Manager survey + export usage |
| Team adoption | 80%+ of target teams active within 90 days | Active-team instrumentation |
| Data-trust score | ≥ 4/5 among engineering leads | In-product survey |
| AI ROI demonstrated | ≥ 1 assistant with a $ figure in first Phase-3 release | AI ROI module output |
| Dashboard load | < 3 s at ~1M events | Frontend performance telemetry |
| Metric freshness | ≤ 15 min from source event | Ingestion → materialization lag metric |

**First-party instrumentation events (required from day one, aggregate-only, no surveillance
content):** `connector_connected`, `first_dashboard_rendered`, `dashboard_viewed` /
`drilldown_opened`, `report_exported`, `goal_created` / `goal_progress_viewed`,
`pr_flag_actioned`, `ai_roi_report_viewed`, `survey_response_submitted`.

## 5. Epic map & traceability

MoSCoW priority is noted on every story. Every story traces BRD → epic → phase → release.

| Epic | Name | BRD source | Phase |
|---|---|---|---|
| E1 | Onboarding & Connectors | FR-1.x, §5.1, §10 | 1 |
| E2 | Identity & Team Normalization | FR-1.4, FR-1.5, §11.1 | 1 |
| E3 | DORA & Delivery Metrics | §8.2, §5.1 | 1 |
| E4 | Cockpit / Executive Dashboard | §8.3, §5.1 | 1 |
| E5 | Investment Profile | §8.4, §5.1 | 2 |
| E6 | Code Review & PR Analytics | §8.5, §5.1 | 2 |
| E7 | Goals & OKR Tracking | §8.7, §5.2 | 2 |
| E8 | Administration & Access Control | §8.8, §9 | 1 |
| E9 | AI Adoption & ROI | §8.6, §5.2 | 3 |
| E10 | AI Code Review Agent | §8.5, §5.2 | 3 |
| E11 | Custom Reporting & Query Layer | §8.9, §5.2 | 3 |

## 6. Detailed requirements by epic

Acceptance criteria are the contract QA and engineering build to.

### E1 · Onboarding & Connectors (Phase 1)

Goal: an admin connects the organization's tools in minutes over secure least-privilege scopes and
reaches first insight in under 30 minutes. MVP connectors: Git host (GitHub and/or
GitLab/Bitbucket), Jira, one CI/CD tool. Should-have: SonarQube, PagerDuty/Opsgenie, AI-assistant
telemetry. Secrets encrypted; per-connector health surfaced to admins.

- **E1-S1 Connect a Git provider [Must]** — OAuth/app install requests only least-privilege read
  scopes; status shows Connected; historical backfill starts automatically with visible progress;
  auth failure/revocation shows an actionable error, never a silent failure; repos/orgs can be
  scoped in/out before ingestion.
- **E1-S2 Connect ticketing (Jira) [Must]** — epics, stories, sprints, status-change history
  ingest within the freshness target; projects map to AI Impact Evaluation teams during setup; non-standard
  workflows are tolerated and reported, not rejected.
- **E1-S3 Connect a CI/CD tool [Must]** — build/deploy events with timestamps and outcomes ingest
  reliably; deployments map to repos/services for DORA attribution; pipeline duration captured.
- **E1-S4 Resilient ingestion [Must]** — transient errors retried with backoff; persistent
  failures raise a connector-health alert; rate limits respected without dropping events
  (queue-and-drain); unexpected upstream schema changes are logged and quarantined rather than
  corrupting metrics.
- **E1-S5 30-minute time-to-value [Must]** — after first Git + ticketing connection, at least one
  populated dashboard renders with no manual configuration; guided setup checklist shows remaining
  steps and data-readiness; connect→first-render duration instrumented against the 30-minute target.

### E2 · Identity & Team Normalization (Phase 1)

Goal: reconcile that one human is `v.sharma` in Git, `Vishal S` in Jira, and `vishal@` in CI, and
roll people up into teams — inaccurate identity resolution is the fastest way to lose leadership
trust.

- **E2-S1 Contributor identity reconciliation [Must]** — multiple tool identities resolving to
  the same person attribute to a single canonical contributor; Admin can review/confirm/merge/split
  suggested matches; unresolved identities are flagged, never silently dropped or double-counted.
- **E2-S2 Team-structure import [Should]** — three-level org > team > sub-team hierarchy imports
  and is editable; reassignments recompute historical roll-ups consistently; teams without a
  source-of-record can be created manually.

### E3 · DORA & Delivery Metrics (Phase 1)

Goal: the four DORA metrics plus lead-time breakdowns, automatically, zero manual tagging,
filterable by team/repo/sprint/date. Formulas: [metric-definitions.md](metric-definitions.md).

- **E3-S1 Automated DORA computation [Must]** — all four metrics compute with no manual tagging
  or status discipline; each shows current value, trend, and comparison to a configurable target
  band; every metric exposes a plain-language definition.
- **E3-S2 Lead-time stage breakdown [Must]** — commit → PR open → review → merge → deploy
  breakdown per team and repo; the slowest stage is visually emphasized; ticket-level lead time
  shown alongside PR-level.
- **E3-S3 Change Failure Rate from incidents [Should]** — deployments linked to a subsequent
  incident/hotfix/rollback count as failures; linkage logic is transparent and Admin-adjustable;
  MTTR derived from incident open/resolve timestamps.
- **E3-S4 Universal filtering [Must]** — team/repo/sprint/date filters apply consistently across
  all metric views and persist within a session; filtered results respect the viewer's access
  scope; empty results explain why, never a blank chart.

### E4 · Cockpit / Executive Dashboard (Phase 1)

- **E4-S1 Single-pane Cockpit [Must]** — headline DORA + PR velocity + epic progress on one
  screen; RAG status per tile against configurable thresholds; loads within 3 s at ~1M events.
- **E4-S2 Org → team → IC drill-down [Must]** — drill-down preserves active filters and time
  range; individual-level drill-down gated by access control AND personal opt-in (E8);
  breadcrumbs navigate back up.
- **E4-S3 Presentation-ready export [Must]** — export produces a shareable formatted artifact
  (PDF/deck-ready) reflecting current filters; completes in seconds; logged as `report_exported`;
  metric definitions accompany the export.

### E5 · Investment Profile (Phase 2)

- **E5-S1 Planned vs unplanned vs rework [Should]** — Git activity correlated to Jira epics
  classifies each unit of work; split shown per team over time with category definitions;
  unclassifiable work bucketed transparently.
- **E5-S2 Scope-creep trend [Should]** — rising unplanned-work trends visually surfaced with
  drill-down into contributing tickets.
- **E5-S3 Cost/time attribution for finance [Could]** — effort attributable to epics/initiatives
  with time-based cost estimates; methodology documented and exportable for audit.

### E6 · Code Review & PR Analytics (Phase 2)

- **E6-S1 Review analytics [Should]** — PR size distribution, review turnaround, reviewer
  workload balance per team/repo; reviewer load shown as *balance*, never an individual
  leaderboard; respects access scope and privacy principles.
- **E6-S2 Proactive PR flags [Should]** — PRs exceeding configurable size/age thresholds flagged
  in-product; acting on a flag emits `pr_flag_actioned`; thresholds team-configurable with
  sensible defaults.

### E7 · Goals & OKR Tracking (Phase 2)

- **E7-S1 Set metric-linked goals [Should]** — a goal binds to a metric, target value, scope, and
  time window; multiple concurrent goals per team; emits `goal_created`.
- **E7-S2 Automatic progress tracking [Should]** — progress recomputes as metrics refresh within
  the freshness target; on-track/at-risk/off-track shown against target trajectory; visible in the
  Cockpit for in-scope leaders.

### E8 · Administration & Access Control (Phase 1)

Roles and default visibility: **Admin** (full config, connectors, roles, audit log) ·
**Engineering Leader** (org-wide aggregated + team drill-down) · **Manager** (own teams only) ·
**IC** (self only, opt-in personal view) · **Finance/Read-only** (aggregated ROI & cost views, no
individual data).

- **E8-S1 Role-based access control [Must]** — the five roles enforce their default visibility;
  Admin can refine team/individual rules; no path (including exports and deep links) leaks data
  outside a user's scope; access changes take effect immediately and are logged.
- **E8-S2 Opt-in, non-surveillance individual views [Must]** — no keystroke, idle-time, or
  activity-surveillance metric exists anywhere; a manager cannot view an individual's personal
  view without the IC's opt-in per policy; every individual-level metric displays its definition
  in growth-oriented, non-ranking language.
- **E8-S3 Audit trail [Must]** — all configuration changes, access grants, and exports logged
  with actor, timestamp, target; retrievable ≥ 12 months; audit log itself access-controlled and
  tamper-evident.

### E9 · AI Adoption & ROI (Phase 3)

- **E9-S1 Track AI-assisted work [Should]** — detection from assistant telemetry and/or commit
  conventions; confidence and method transparent; low-confidence attribution labeled; adoption
  per team over time.
- **E9-S2 AI vs non-AI delta [Should]** — cycle time, defect rate, and rework compared side by
  side with clear methodology; comparisons control for obvious confounders where feasible and
  state their limits.
- **E9-S3 Dollar ROI figure [Should]** — at least one assistant yields a $ ROI figure in the
  first Phase-3 release; calculation assumptions visible and adjustable; high-value usage patterns
  and low-adoption pockets surfaced for enablement.

### E10 · AI Code Review Agent (Phase 3)

- **E10-S1 First-pass automated review [Could]** — on PR open, the agent posts advisory comments
  (style, likely bugs, security), clearly attributed to the agent; humans remain the deciders;
  enable/disable per repository.
- **E10-S2 Review quality feedback loop [Could]** — before/after review-time and defect-leakage
  effects reported; false-positive rate tracked.

### E11 · Custom Reporting & Query Layer (Phase 3)

- **E11-S1 Pre-built report library [Should]** — covers the most common use cases out of the box
  (cycle time, bug resolution, DORA workflow); filterable and exportable; no query language
  required.
- **E11-S2 Guided custom-metric builder [Could]** — custom metrics composed through a guided UI,
  not raw query text; saveable, shareable within scope, exportable; an advanced/raw mode may exist
  but is never required.

## 7. Key user flows

1. **First-run (Admin):** sign-in → guided setup checklist → connect Git (backfill with visible
   progress) → connect Jira + CI/CD, map to teams → identities normalized, unresolved matches
   flagged → first populated dashboard. *Success: populated DORA/Cockpit within 30 minutes, no
   query-writing, no engineer behavior change.*
2. **Board prep (Exec):** open Cockpit (RAG status) → drill amber Lead-Time tile org → team →
   quarter filter, confirm trend + linked goal → export presentation-ready report with definitions
   attached. *Success: board-ready report in under two minutes.*
3. **Sprint health (Manager/Tech Lead):** filter Team Dashboard to sprint → breakdown highlights
   review as slowest stage → open Code Review Analytics, stale oversized PR flagged → rebalance
   reviewer load, follow up (`pr_flag_actioned`). *Success: bottleneck actioned within the sprint;
   no individual ranked or shamed.*

## 8. Information architecture

Role-aware navigation — users see only surfaces their role and scope permit:
**Cockpit** (Exec, Leader) · **Teams** (Manager, Leader) · **Code Review** (Tech Lead, Manager) ·
**Investment** (PM, Finance, Leader) · **Goals** (Leader, Manager) · **AI Insights** (Ph.3 — Exec,
Finance, Leader) · **Reports** (Ph.3 — all analytical roles) · **Personal** (opt-in — IC) ·
**Admin** (Admin, Security).

## 9. UX & design requirements

Insight-first layout (answer first, raw detail one drill-down away) · definitions on demand inline
for every metric · consistent RAG semantics against stated configurable targets · filters persist
and travel through drill-downs and exports within a session · growth-framed individual UX, never
leaderboards · WCAG-conscious contrast and keyboard navigation; legible on laptop and large
display · empty/loading states explain themselves (no data / out of range / still ingesting).

## 10. Non-functional requirements

Same as BRD §9 (see [brd-summary.md](brd-summary.md#non-functional-requirements-brd-9)):
< 3 s dashboards at ~1M events · ≤ 15 min metric freshness · horizontal ingestion scaling,
10,000+ contributors · SOC 2 Type II readiness (P2/3), encryption in transit/at rest,
least-privilege scopes · no surveillance metrics by design, opt-in individual views · 99.5%
uptime, graceful ingestion degradation · < 30 min time-to-value · 12+ month audit retention ·
P1 SaaS multi-tenant, P3 VPC/on-prem ingest · new connectors without core re-architecture.

## 11. Data & privacy

Sources per BRD §10 (Git, Jira/PM, CI/CD, SonarQube, incidents, AI assistants, optional
Slack/calendar), always least-privilege. **Privacy guarantees that must hold across all data:**
no keystroke/idle-time/activity-surveillance data collected *or derivable* — full stop ·
individual views opt-in and access-gated, aggregate/team views default · metric definitions always
transparent · data residency (VPC/on-prem ingest) may be needed earlier than Phase 3 for specific
engagements.

## 12. Release plan

| Phase | Duration | Epics | Exit criteria |
|---|---|---|---|
| 0 — Discovery | 2–3 wks | PRD sign-off, C4 model, pilot selection | BRD + PRD approved; pilots named; build-vs-buy quotes in |
| 1 — MVP | 8–10 wks | E1, E2, E3, E4, E8 | Git+Jira+1 CI/CD live; DORA + Cockpit + RBAC; 30-min TTV met |
| 2 — Depth | 6–8 wks | E5, E6, E7 (+ code-quality connector) | Investment Profile, PR analytics, Goals shipped to pilot |
| 3 — AI & Advanced | 8–12 wks | E9, E10, E11 (+ VPC/on-prem option) | AI ROI $ figure; custom reports; review agent piloted |
| 4 — Commercialize (opt.) | Ongoing | Multi-tenant packaging, SOC 2 Type II | External onboarding ready (only if commercial path chosen) |

**MVP definition (Phase 1 exit):** a pilot team connects Git + Jira + one CI/CD tool, reaches a
populated DORA Cockpit in under 30 minutes, drills org→team with RBAC enforced, and exports a
leadership-ready report — zero manual tagging, zero surveillance metrics.

## 13. Dependencies, assumptions, constraints, out of scope

**Dependencies:** third-party API access/scopes (blocks all metrics) · pilot team availability ·
engineering sponsorship for adoption · AI-assistant telemetry or commit conventions (weakens E9
attribution if absent) · C4 model from Technical Architect · build-vs-buy decision.
**Assumptions/constraints/out-of-scope:** per BRD §12/§5.3 — supported Git host + ticketing in
place; 2–3 pilot teams; build-vs-buy budget not yet approved; vendor rate limits constrain refresh;
surveillance permanently excluded; no tool replacement; HR review automation deferred.

## 14. Open questions (must close Phase 0)

| ID | Question | Owner | Blocks |
|---|---|---|---|
| OQ-1 | Internal tool, commercial SaaS, or both? | Sponsor + Product | Phase 4 scope, pricing |
| OQ-2 | Build, buy, or hybrid vs Hivel/minware? | Finance/Procurement | Build scope & budget |
| OQ-3 | Which 2–3 teams are the Phase-1 pilot? | Sponsor | Phase 1 start |
| OQ-4 | Which single CI/CD tool ships first in MVP? | Technical Architect | E1, E3 scope |
| OQ-5 | Pricing model if commercialized (flat-team recommended)? | Product + Finance | Phase 4 |
| OQ-6 | Is VPC/on-prem needed before Phase 3 for any engagement? | Security + Sales | Deployment architecture |
| OQ-7 | What is the org's individual-view opt-in policy? | People + Security | E8 personal views |

## Appendix A · Glossary

Product-facing definitions live in the PRD docx Appendix A and must appear inline in-product
wherever the corresponding metric is shown. Engineering-grade formulas, data sources, and edge
cases live in [metric-definitions.md](metric-definitions.md) — that file is the computational
source of truth.

## Appendix B · Delivery status

Repo-only appendix (not in the signed docx) — updated as increments land. Legend: ✅ done ·
🟡 partial · ⬜ not started.

| Epic / story | Status | Notes |
|---|---|---|
| E1-S1 Git provider | 🟡 | `connector-github`: signature-verified webhooks + PR/commit backfill live; GitHub App install flow, repo scoping UI, GitLab pending |
| E1-S2 Jira | 🟡 | `connector-jira`: token-verified webhooks + issue backfill with changelogs; project→team mapping UI pending |
| E1-S3 CI/CD | 🟡 | GitHub Actions via `connector-github` (workflow-run backfill + live events); deployment-rule mapping pending (OQ-4 answered as GitHub Actions for pilot) |
| E1-S4 Resilient ingestion | 🟡 | Queue + retry + DLQ + idempotent staging writes live and smoke-verified; `GET /api/v1/admin/connectors` (ADMIN) now surfaces per-connector status/last-sync/event-count derived from `staging.raw_event`, rendered live in the Admin console; proactive alerting (paging) on top of this signal still pending |
| E1-S5 30-min TTV | 🟡 | `GET /api/v1/setup/status` (ADMIN/ENG_LEADER) derives a 4-item checklist (git/ticketing/CI/dashboard) and a `firstConnectionAt`→`firstDashboardAt` time-to-value figure entirely from `staging.raw_event`/`mart.metric_daily` timestamps — no manual flags; frontend Setup view renders it. Guided remediation steps (e.g. deep links to reconnect a failed connector) pending |
| E2-S1 Identity reconciliation | 🟡 | `identity-service`: alias resolution, exact-email merge (confidence-scored), bot detection, git-signature fallback; Admin review/merge/split UI pending |
| E2-S2 Team import | 🟡 | `connector-github` fetches org teams + repos + members; `identity-service` upserts `core.team`/`core.team_repo`/`core.team_member`, resolving members through the existing identity resolver; sub-team hierarchy (`parent_team_id`) not yet populated |
| E3-S1 DORA metrics | 🟡 | `metrics-engine` + mart: **all four DORA metrics** live end-to-end — deployment frequency (DORA-1), lead time (DORA-2, heuristic PR-open→deploy), change failure rate (DORA-3), MTTR (DORA-4) via hotfix/rollback linkage; plus PR velocity/cycle-time — each at **repo, org, and team** scope. RAG target bands and incident-based CFR (Phase 2) pending |
| E3-S2 Lead-time stages | 🟡 | Total lead time + deploy-wait computable; commit→PR and review-wait/review stages need PR-review + commit-in-PR ingestion |
| E3-S4 Filtering | 🟡 | Repo/org/team scope + date-window filters live in the API (`scope` param); sprint dimension pending |
| E4-S1 Cockpit | 🟡 | Real tiles from `/api/v1/metrics/cockpit` with freshness indicator, loading/error/empty states, definitions on hover; RAG thresholds and epic progress pending |
| E4-S2 Org → team drill-down | 🟡 | `GET /api/v1/teams` + Cockpit `scope` param let the frontend list teams and drill into team-scoped tiles (smoke-verified); IC-level drill-down and breadcrumb-preserved filters beyond team pending |
| E8-S1 RBAC | 🟡 | api-core enforces five roles server-side (JWT resource server, ADR-0004); metrics + teams endpoints gated to analytical roles, IC denied; dev-token bridge pending OIDC; Admin console now renders live connector health and audit log (frontend `Admin.tsx` wired to `/api/v1/admin/connectors` + `/api/v1/audit`), replacing two of its three mock panels; role-assignment management still mock (no persisted user directory yet — needs OIDC/user-management backend) and per-user team/individual visibility rules pending |
| E8-S2 Opt-in individual views | 🟡 | Invariant enforced (no surveillance metrics exist; IC denied org/team metrics); personal-view endpoint + opt-in gate pending its surface |
| E8-S3 Audit trail | 🟡 | Append-only audit log with admin-only read; token issuance audited; config/export events wire in as those surfaces land; cryptographic tamper-evidence is later hardening |
| E5–E7, E9–E11 | ⬜ | Phase 2/3 |
