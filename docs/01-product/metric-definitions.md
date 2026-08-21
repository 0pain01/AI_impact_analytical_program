# Metric Definitions — AI Impact Evaluation

Single source of truth for every metric AI Impact Evaluation computes. **Rules:** (1) no metric ships
without a definition here; (2) definitions are published in-app verbatim — metric transparency
is a trust requirement (BRD §13 risk 1); (3) changes to a formula bump the metric logic
version, and recomputed history records which version produced it.

Conventions: all timestamps UTC internally; date-window boundaries evaluated in the org's
configured timezone. Bot accounts excluded from people-dimensions by default. "Deployment"
means a production deployment as detected by the per-repo deployment rule (see F3 in PRD).

**v1 implemented (2026-07-05):** all four DORA metrics compute in `metrics-engine` from staged
GitHub events, at **repo**, **org** (`*`), and **team** scope, materialized into
`mart.metric_daily` (`scope_id`/`scope_type` columns — E4-S2). Team scope is joined via
`core.team_repo`, populated by the identity service's team import (E2-S2); percentiles for team
scope are computed from the underlying per-event population, never by averaging repo-day
medians. Current fidelity vs. the full definitions below:
- **DORA-1** as specified.
- **DORA-2 lead time:** start point is **PR opened** (not first commit — commit-in-PR history not
  yet ingested); linkage is the first successful production deploy on the PR's repo at/after
  merge. The commit→PR and review-stage breakdown await PR-review ingestion.
- **DORA-3 CFR / DORA-4 MTTR:** failure signal is a **hotfix/rollback deployment** (workflow name
  matching `hotfix|rollback|revert`) within 48h of a deployment on the same repo. Incident-linkage
  (PagerDuty) arrives with the Phase-2 incident connector; the definitions below already describe it.
Deploy/hotfix detection patterns are per-repo configurable (`METRICS_DEPLOY_WORKFLOW_PATTERN`,
`METRICS_HOTFIX_WORKFLOW_PATTERN`).

---

## DORA-1: Deployment frequency — v1

- **BRD:** FR-8.2.1, BO-2
- **Definition:** number of successful production deployments per unit time.
- **Formula:** `count(successful production deployment events)` per day/week, per scope
  (org / team / repo). Displayed as a rate plus DORA performance band.
- **Sources:** CI/CD connector deployment events (environment = production per repo rule).
- **Dimensions:** team, repository, date range.
- **Edge cases:**
  - Failed/cancelled deployments excluded from the count (they enter CFR's denominator only
    if they reached production).
  - Rollback deployments count as deployments (they are real deploys) but are also flagged
    as failure signals for CFR.
  - Multi-repo/monorepo services: frequency is per configured deployable unit, default repo.
  - Backfilled history before connector install is included when the CI/CD API provides it.

## DORA-2: Lead time for changes — v1

- **BRD:** FR-8.2.1–8.2.3, BO-2
- **Definition:** time from first commit of a change until that change runs in production.
  Reported as **median** (p50) with p75/p90 available; means are not used (outlier-dominated).
- **Formula:** for each PR included in a deployment:
  `deploy_finished_at − min(authored_at of commits unique to the PR)`. Median over the window.
- **Stage breakdown (each a median over the same population):**
  1. Coding: first commit → PR opened
  2. Review wait: PR opened → first review activity
  3. Review: first review activity → merge
  4. Deploy wait: merge → deployment finished
- **Sources:** Git connector (commits, PRs, reviews), CI/CD connector (deployments), linked
  by commit SHA reachability from the deployed ref.
- **Edge cases:**
  - **Squash/rebase merges:** original authored timestamps are lost on the merged commit —
    use the PR's own commit history (pre-merge) captured at ingestion for `authored_at`.
  - **Force-pushed branches:** earliest authored timestamp among commits present at merge
    time; discarded commits don't count.
  - Direct pushes to main (no PR): lead time = deploy − authored_at; stage breakdown omitted.
  - Reverted-then-relanded changes count as two changes.
  - Stale authored dates (rebases carrying old dates, imports): values > 90 days are capped
    and flagged in the UI rather than silently skewing the median.
  - Changes never deployed are excluded (they have no lead time yet), but PR-level cycle
    time (open → merge) is reported separately so undeployed work stays visible.

## DORA-2b: Ticket lead time — v1

- **BRD:** FR-8.2.3 (minware-style ticket-level lead time)
- **Definition:** time from a ticket entering an in-progress status to the deployment of the
  **last** PR linked to it. Median per scope.
- **Sources:** Jira status transitions + PR↔ticket linkage (branch name, PR title/body issue
  keys, Jira dev-panel links).
- **Edge cases:** tickets with no linked PR are excluded from this metric (reported in an
  "unlinked work" quality indicator instead — feeds Phase 2 Investment Profile); reopened
  tickets restart at the latest in-progress transition; multi-PR tickets use last deploy.

## DORA-3: Change failure rate — v1

- **BRD:** FR-8.2.4
- **Definition:** percentage of production deployments causing a failure requiring remediation.
- **Formula:** `failed_deployments / total_deployments` over the window, where a deployment
  is *failed* if within 48 h it is followed by (a) a linked incident (Phase 2, incident
  connector), (b) a rollback event on the same deployable unit, or (c) a hotfix deploy —
  a deploy whose changes come from a branch/PR matching hotfix conventions (label `hotfix`,
  branch `hotfix/*`) touching the same unit.
- **MVP confidence labeling:** until the incident connector ships (Phase 2), CFR uses
  heuristics (b) and (c) only and is displayed with a "heuristic-based" confidence badge.
- **Edge cases:** multiple failure signals for one deploy count once; a rollback deploy is
  itself excluded from the failure numerator (it's the remediation, not the failure);
  cascading hotfix chains attribute failure to the earliest deploy in the chain.

## DORA-4: Mean time to restore (MTTR) — v1

- **BRD:** FR-8.2.1
- **Definition:** median time from failure detection to service restoration for production
  failures. (Name kept as MTTR for industry familiarity; statistic is the median.)
- **Formula (MVP, heuristic):** `remediation_deploy_finished_at − failed_deploy_finished_at`
  for deployments marked failed by DORA-3 rules (b)/(c).
- **Formula (Phase 2, incident-based):** `incident_resolved_at − incident_opened_at` for
  production incidents; heuristic fallback retained for orgs without an incident tool.
- **Edge cases:** unresolved incidents excluded until resolved (shown as "ongoing"); restore
  windows crossing the query boundary attributed to the window containing restoration.

---

## Supporting metrics (Cockpit, MVP)

| Metric | Definition (v1) | Source |
|---|---|---|
| PR velocity | Merged PRs per week per scope | Git connector |
| PR cycle time | Median open → merge | Git connector |
| Open-PR aging | Open PRs bucketed by age; stale = no activity > 7 days (configurable) | Git connector |
| PR size | Diff lines (additions + deletions) distribution; oversized default > 400 lines | Git connector |
| AI-assisted commits (F8) | Count/share of commits with AI attribution trailers (Copilot/Claude/Cursor conventions) — trend only in MVP | Git connector |

---

## Time to value (TTV) — v1 (E1-S5)

- **BRD:** FR-1.1 (30-minute time-to-value onboarding target).
- **Definition:** elapsed time from the organization's first ingested Git/ticketing event to its
  first computed dashboard metric — i.e. how long it took to go from "connected a tool" to
  "saw a real number."
- **Formula:** `minutesToValue = firstDashboardAt - firstConnectionAt`, where
  `firstConnectionAt = MIN(staging.raw_event.received_at) WHERE source IN ('github', 'jira')` and
  `firstDashboardAt = MIN(mart.metric_daily.computed_at)`. `withinTarget = minutesToValue <= 30`.
- **Sources:** `staging.raw_event`, `mart.metric_daily` (api-core, read-only) — no new table;
  fully derived from timestamps the ingestion/metrics pipelines already write, per the
  no-manual-tagging rule.
- **Companion checklist:** `GET /api/v1/setup/status` also reports four booleans (git/ticketing/
  ci/dashboard connected) each derived the same way — `EXISTS(... staging.raw_event WHERE
  source = 'github' AND event_type NOT LIKE 'workflow_run%' ...)` for git, `LIKE 'workflow_run%'`
  for CI (both connectors publish under `source = 'github'`), `source = 'jira'` for ticketing,
  and `EXISTS(mart.metric_daily)` for the dashboard.
- **Edge cases:** both timestamps null until any event/metric exists — `minutesToValue` and
  `withinTarget` stay null (never a misleading 0) rather than defaulting; the figure is org-wide,
  not per-user, so it reflects the org's first-ever onboarding rather than each admin's session.

---

## AI adoption, spend & ROI — v1 (E9)

> **Status (2026-08-21):** All five metrics are **live**. AI-01 (spend), AI-02 (cost per
> PR/dev-day), and AI-03 (adoption rate) are computed by `AiCostTrackQueryService` in api-core
> from `staging.ai_usage_state` (populated by `connector-ai-telemetry` reading Claude Code /
> GitHub Copilot usage-report files — see that connector's README for the file→real-API swap
> seam). AI-03's adoption rate is combined across tools, not per-tool, since Claude Code and
> Copilot identities aren't cross-tool resolved yet (an engineer using both counts as two
> distinct active users); the query service caps the computed rate at 100% to compensate.
> **AI-04** (cycle-time delta, a reduced scope of the full AI-assisted-vs-non-AI delta — CFR and
> rework segmentation aren't computed, since deploy↔PR linkage doesn't exist in the schema) and
> **AI-05** (dollar ROI) went live the same day: `staging.pull_request_state.ai_assisted` (V13
> migration) is detected from each PR's title/body/labels against known AI co-author trailer
> conventions (Claude Code's `Co-authored-by: Claude` / `Generated with Claude Code`, GitHub
> Copilot's equivalents) — the same heuristic the "AI-assisted commits" supporting metric above
> already specified, applied to PRs since that's what's in the ingested payload. Both are `null`
> in the API (not a fabricated zero) when a window has fewer than 3 merged PRs in either bucket.
> All figures are computed by the real formula against sample usage/PR data shaped like each
> vendor's real API schema — not a genuine enterprise export yet, and the UI says so via a
> "Demo data · real API schema" badge rather than "Live". The methodology note (surfaced in both
> the API response and the UI) explicitly caveats trailer-based attribution's undercount risk and
> names all four org-configurable assumptions (Copilot seat cost, licensed seats, blended hourly
> rate, and the 3-PR minimum-sample threshold).

- **AI-01 Total AI spend:** `BRD:` BO-3, PG-3. **Definition:** sum of AI coding-assistant license
  and usage-based (token) cost across connected tools for the window. **Formula:**
  `Σ(license_seat_cost + metered_token_cost)` per tool, summed org-wide; also broken out per tool.
  **Sources:** `ai-telemetry` connectors (Copilot, Cursor, Claude Code usage/billing APIs).
  **Edge cases:** un-metered/flat-fee tools use allocated seat cost; unconnected tools excluded
  and flagged in Admin rather than assumed zero.
- **AI-02 Cost per PR / cost per dev / day:** **Definition:** total AI spend normalized by
  AI-assisted PR volume or active licensed engineers. **Formula:** `total_spend / count(PRs with
  AI attribution)` and `total_spend / (active_days × licensed_seats)`. **Sources:** as AI-01 plus
  Git connector AI-attribution trailers (see "AI-assisted commits" supporting metric above).
- **AI-03 Adoption rate (E9-S1):** **Definition:** share of active engineers with ≥1 AI-attributed
  commit/PR in the window, per team and org-wide. **Formula:** `distinct(engineers with AI
  attribution) / distinct(active engineers)`. Detection method (telemetry vs. commit-trailer
  heuristic) and confidence are shown alongside the number per BRD §5.2; low-confidence
  attribution is labeled, never silently folded into the total.
- **AI-04 AI-assisted vs. non-AI delta (E9-S2):** **Definition:** cycle time, change failure
  rate, and rework rate compared side by side for AI-assisted vs. non-AI-assisted work in the
  same window. **Formula:** existing DORA-2/DORA-3 and rework-rate computations, segmented by the
  AI-attribution flag on the underlying PR. **Edge cases:** methodology and confounder caveats
  (team mix, PR size mix) are surfaced in-UI, not just in this doc — comparisons never claim
  causation.
- **AI-05 Dollar ROI figure (E9-S3):** **Definition:** estimated monthly value recovered from
  AI-assisted work versus AI spend. **Formula:** `estimated_hours_saved × blended_hourly_rate`,
  where `estimated_hours_saved = ai_assisted_pr_count × (nonAi_cycle_time_p50 −
  ai_assisted_cycle_time_p50)`; `roi_multiple = dollar_value_recovered / total_ai_spend`.
  **Edge cases:** `blended_hourly_rate` and attribution method are org-configurable assumptions,
  shown alongside the figure per the BRD's "calculation assumptions visible and adjustable"
  requirement — never presented as a hidden constant.

## Changelog

- 2026-08-21 (2) — AI-04/AI-05 went live: `staging.pull_request_state.ai_assisted` (V13
  migration) detects AI co-author trailers on PR title/body/labels; `AiCostTrackQueryService`
  segments merged-PR cycle time by that flag (AI-04) and derives hours-saved/dollar-value/ROI-
  multiple from the delta (AI-05), both `null` below a 3-PR-per-bucket sample floor. Verified
  against real data already in the dev DB: 5 genuinely AI-attributed PRs in a real connected
  repo (`santifer/career-ops`, real `Co-authored-by: Claude` trailers) produced a real negative
  ROI (-4.8×, AI-assisted PRs were slower in that small sample) — reported as-is, not floored,
  with a small-sample caveat in the UI. AI Cost Track's "Live" badge changed to "Demo data · real
  API schema" since the two usage-report files remain sample data shaped like each vendor's real
  API, not a genuine enterprise export.
- 2026-08-21 — AI-01/AI-02/AI-03 went live: new `connector-ai-telemetry` (Claude Code + Copilot
  usage-report ingestion) and `staging.ai_usage_state` feed `AiCostTrackQueryService`; AI Cost
  Track's Spend and Adoption tabs now show real computed figures instead of mock data. Fixed an
  adoption-rate-over-100% bug (combined-across-tools double counting) by capping at 100% with an
  in-UI methodology note. AI-04/AI-05 still pending PR-level AI-attribution.
- 2026-07-14 — time-to-value (TTV) definition authored (E1-S5): `GET /api/v1/setup/status`
  serves the onboarding checklist + `minutesToValue`/`withinTarget`, derived from existing
  staging/mart timestamps.
- 2026-07-06 — AI adoption/spend/ROI definitions authored (E9, UI-first): AI-01..AI-05; shipped
  as the "AI Cost Track" frontend view against mock data ahead of `ai-telemetry` connectors.
- 2026-07-05 — team scope added (E4-S2): all six metrics now materialize at repo, org, and team
  level; team percentiles computed from the raw per-event population, not repo-median averaging.
- 2026-07-05 — DORA-2/3/4 implemented at v1 heuristic fidelity (see "v1 implemented" note at top);
  `deployment_frequency`, `pr_velocity`, `pr_cycle_time_p50_hours` already shipped 2026-07-04.
- 2026-07-04 — v1 definitions authored for MVP (all above).
