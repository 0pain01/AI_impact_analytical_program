# Functional Requirements Document — AI Impact Evaluation (v1.0)

- **Status:** v1.0 — Draft for stakeholder review (pending sign-off)
- **Date:** 2026-07-27 · **Authors:** Vishal & Aditi
- **Upstream:** BRD v1.0 ([brd-summary.md](brd-summary.md), §8 functional-requirement modules,
  FR-1.x numbering) and PRD v1.0 ([prd.md](prd.md), Section 5 epic map, Section 6 epic detail).
  The BRD answers *why*, the PRD answers *what, for whom, to what acceptance standard*; this FRD
  answers *exactly how each function behaves* — inputs, processing, outputs, business rules, and
  error/edge-case handling — as the contract between product, engineering, and QA.
- **Downstream:** C4 architecture model ([../03-architecture/system-architecture.md](../03-architecture/system-architecture.md)),
  metric formulas ([metric-definitions.md](metric-definitions.md)), per-service READMEs, OpenAPI specs.
- **Traceability convention:** every requirement below carries an FR ID. FR-1.x IDs are inherited
  verbatim from BRD §8.1; all other FR ranges are introduced here and map 1:1 to PRD epics/stories
  (e.g. FR-3.x ↔ E3-Sx). No FR may exist without a tracing epic/story, and no epic/story ships
  without a corresponding FR.

## 1. Purpose and scope

This FRD specifies the observable, testable behavior of every function in AI Impact Evaluation
Phases 1–3: data ingestion and identity resolution, DORA/delivery metrics, the executive Cockpit,
Investment Profile, code-review analytics, goals/OKRs, administration/RBAC, AI adoption/ROI, the AI
review agent, and custom reporting. It excludes visual design (Section 9 of the PRD), infrastructure
sizing (system-architecture.md), and metric math derivations (metric-definitions.md) — this document
states *what the system must do*, those documents state *how it looks* and *how it's computed/built*.

Non-negotiable constraints inherited from the BRD apply to every function in this document without
exception: no individual-level surveillance function (keystroke, idle-time, screen monitoring) may
ever be specified or implemented; no function may require an engineer to manually tag work to
produce a metric; the platform is an analytics layer only and no function writes back to a
connected tool except the Phase 3 AI review agent posting advisory PR comments.

## 2. Functional module index

| Module | FR range | Epic(s) | Phase |
|---|---|---|---|
| Data Integration & Connectors | FR-1.1 – FR-1.8 | E1 | 1 |
| Identity & Team Normalization | FR-2.1 – FR-2.2 | E2 | 1 |
| DORA & Delivery Metrics | FR-3.1 – FR-3.4 | E3 | 1 |
| Cockpit / Executive Dashboard | FR-4.1 – FR-4.3 | E4 | 1 |
| Administration & Access Control | FR-8.1 – FR-8.3 | E8 | 1 |
| Investment Profile | FR-5.1 – FR-5.3 | E5 | 2 |
| Code Review & PR Analytics | FR-6.1 – FR-6.2 | E6 | 2 |
| Goals & OKR Tracking | FR-7.1 – FR-7.2 | E7 | 2 |
| AI Adoption & ROI | FR-9.1 – FR-9.3 | E9 | 3 |
| AI Code Review Agent | FR-10.1 – FR-10.2 | E10 | 3 |
| Custom Reporting & Query Layer | FR-11.1 – FR-11.2 | E11 | 3 |

## 3. Data Integration & Connectors (FR-1.x, Epic E1, Phase 1)

### FR-1.1 Connect a source-control provider (Must)
- **Input:** Admin-initiated OAuth/App-install flow against GitHub and/or GitLab/Bitbucket; scope
  selection (org/repo allow-list).
- **Processing:** request least-privilege read scopes only (code, PR, commit metadata — never
  write); on grant, persist encrypted credentials; enqueue historical backfill job; expose
  per-repo include/exclude toggles before ingestion starts.
- **Output:** connector status = Connected/Disconnected/Error; backfill progress percentage;
  event stream of commits, PRs, reviews, branches into the raw ingestion layer.
- **Business rules:** a revoked or expired token immediately flips status to an actionable error
  state (never silently stops); repos toggled out mid-backfill stop ingesting within one polling
  cycle without deleting already-ingested history.
- **Error handling:** OAuth failure surfaces the provider's error reason to the Admin, not a
  generic failure; rate-limit responses pause and resume without data loss (see FR-1.4).

### FR-1.2 Connect ticketing/PM tool — Jira (Must)
- **Input:** Jira OAuth/API token; project selection.
- **Processing:** ingest epics, stories, sprints, and full status-change history (changelog);
  map selected Jira projects to AI Impact Evaluation teams during setup.
- **Output:** ticket records with status-transition timestamps available to the metrics engine
  within the freshness target (≤ 15 min, NFR).
- **Business rules:** non-standard workflows (custom statuses, skipped transitions) are ingested
  and reported as anomalies, never rejected outright; unmapped projects are held in a pending
  queue, not silently dropped.

### FR-1.3 Connect a CI/CD tool (Must)
- **Input:** CI/CD provider credentials (GitHub Actions, Jenkins, GitLab CI, Azure Pipelines —
  first shipped tool decided per OQ-4).
- **Processing:** ingest build/deploy events with start/end timestamps, outcome (success/fail),
  and pipeline duration; map deployments to repos/services for DORA attribution.
- **Output:** deployment event stream keyed to repo/service, consumed by FR-3.1 (deployment
  frequency) and FR-3.2 (lead time).
- **Business rules:** a deployment event with no resolvable repo/service mapping is quarantined
  (FR-1.4) rather than silently discarded or mis-attributed.

### FR-1.4 Resilient, idempotent ingestion pipeline (Must)
- **Input:** all inbound webhook/poll events from FR-1.1–FR-1.3, FR-1.6, FR-1.7.
- **Processing:** transient errors (5xx, timeout) retry with exponential backoff; persistent
  failures raise a connector-health alert and stop consuming that source only; rate-limited
  responses queue-and-drain rather than drop events; duplicate deliveries are deduplicated by
  idempotency key so re-processing never double-counts a metric; unexpected upstream schema
  changes are logged and the affected payload is quarantined for review, never silently coerced.
- **Output:** connector health status (Healthy/Degraded/Down) with last-successful-sync
  timestamp and event count, surfaced to the Admin console (FR-8.1).
- **Business rules:** zero data loss on any single-connector outage is a defect-bar requirement,
  not an aspiration — verified via connector integration tests against recorded API fixtures.

### FR-1.5 Team-structure import (Should)
- See FR-2.2 (team import is executed by identity-service but triggered from connector data).

### FR-1.6 Connect code-quality tool — SonarQube (Should)
- **Input:** SonarQube API token/URL.
- **Processing:** ingest per-repo quality-gate results, code-smell/vulnerability counts, and
  coverage trend at a configurable cadence.
- **Output:** quality signal available to Code Review Analytics (FR-6.1) and Investment Profile
  cost/quality correlation (FR-5.3).
- **Business rules:** quality-gate connector outage degrades gracefully — quality tiles show
  "stale as of [time]" rather than blank or last-known-good silently re-labeled as current.

### FR-1.7 Connect AI-assistant telemetry (Should)
- **Input:** Copilot/Cursor/Claude Code telemetry export or commit-convention signal (e.g.
  co-author trailers, tool-specific metadata).
- **Processing:** normalize heterogeneous vendor telemetry formats into a common
  "AI-assisted work unit" schema; where telemetry is unavailable, fall back to commit-convention
  heuristics with an explicit confidence score.
- **Output:** AI-assistance attribution per commit/PR, consumed by FR-9.1.
- **Business rules:** low-confidence attributions (heuristic-only, no vendor telemetry) are
  labeled as such wherever displayed — never presented with the same confidence as
  telemetry-confirmed attribution.

### FR-1.8 No-data-loss guarantee across all connectors (Must)
- **Processing:** every connector implements the shared retry/backoff/DLQ/idempotency contract
  defined in FR-1.4; connector integration tests run against recorded fixtures of each vendor's
  documented failure modes (rate limit, outage, malformed payload, auth revocation).
- **Output:** a passing integration-test suite per connector is a merge gate (engineering
  standards: no merge with failing/skipped tests).

## 4. Identity & Team Normalization (Epic E2, Phase 1)

### FR-2.1 Contributor identity reconciliation (Must)
- **Input:** raw identity strings from each connector (Git handle, Jira account, CI actor, AI
  telemetry user).
- **Processing:** match candidate identities via exact-email match (highest confidence), alias
  tables, and git-signature fallback; each match carries a confidence score; matches below a
  configurable threshold are queued for Admin review rather than auto-merged.
- **Output:** a canonical `contributor` record per human, with linked source identities; an
  Admin review/confirm/merge/split UI acting on the queue.
- **Business rules:** an identity is never double-counted across two canonical contributors and
  never silently dropped for being unresolved — unresolved identities remain visible, flagged,
  and excluded from per-contributor roll-ups until resolved (they still count at team/org level
  where attribution to an individual isn't required).

### FR-2.2 Team-structure import and maintenance (Should)
- **Input:** org/team/repo/member data from source-control provider (e.g. GitHub org teams).
- **Processing:** import a three-level hierarchy (org > team > sub-team); resolve members
  through FR-2.1's identity resolver; allow manual creation of teams with no source-of-record and
  manual editing of the imported hierarchy.
- **Output:** `team`, `team_repo`, `team_member` records consumed by every team-scoped metric and
  filter (FR-3.4, FR-4.2, FR-8.1).
- **Business rules:** a team reassignment recomputes historical roll-ups consistently — a
  contributor's past work reflects their team membership at the time worked or the current
  mapping, per a single documented, consistent rule (not a mix of both).

## 5. DORA & Delivery Metrics (Epic E3, Phase 1)

### FR-3.1 Automated DORA metric computation (Must)
- **Input:** normalized deployment (FR-1.3), commit/PR (FR-1.1), and incident-linkage (FR-3.3)
  events.
- **Processing:** compute deployment frequency, lead time for changes, change failure rate, and
  MTTR per the formulas in metric-definitions.md, with zero manual tagging or status discipline
  required from engineers.
- **Output:** each metric exposes current value, trend line, comparison against a configurable
  target band, and an inline plain-language definition; available at repo, team, and org scope.
- **Business rules:** a metric with insufficient underlying data shows an explicit
  "insufficient data" state, never a fabricated zero or misleading blank chart.

### FR-3.2 Lead-time stage breakdown (Must)
- **Input:** commit timestamps, PR-open timestamp, review events, merge timestamp, deploy
  timestamp.
- **Processing:** decompose total lead time into commit→PR-open, PR-open→review-start,
  review→merge, and merge→deploy stages per team/repo; identify and visually emphasize the
  slowest stage; compute ticket-level lead time (Jira status-change history) alongside PR-level.
- **Output:** stage-breakdown chart per team/repo; ticket-level and PR-level lead time shown
  side by side.
- **Business rules:** a stage with missing source data (e.g. review events not yet ingested) is
  labeled as unavailable, not computed as zero.

### FR-3.3 Change Failure Rate and MTTR via incident linkage (Should)
- **Input:** deployment events (FR-1.3), incident/hotfix/rollback events (PagerDuty/Opsgenie or
  Git hotfix-branch convention).
- **Processing:** a deployment is classified as a failure if a linked incident, hotfix, or
  rollback occurs within a configurable follow-up window; MTTR is derived from incident
  open→resolve timestamps.
- **Output:** CFR and MTTR values feeding FR-3.1; linkage decisions are inspectable (which
  deployment linked to which incident and why).
- **Business rules:** linkage logic (window length, matching heuristic) is Admin-adjustable and
  changes are audit-logged (FR-8.3); ambiguous linkages are flagged for review rather than
  silently assumed.

### FR-3.4 Universal filtering (Must)
- **Input:** team, repo, sprint, and date-range selectors.
- **Processing:** apply the active filter set consistently across every metric view; persist
  filter state within a session and across drill-downs/exports; intersect filter scope with the
  viewer's access scope (FR-8.1) before rendering.
- **Output:** filtered metric views; an explanatory empty state ("no deployments in this range,"
  "team has no repos in scope") whenever a filter combination yields no data.
- **Business rules:** filters never silently widen beyond a user's access scope even if a broader
  filter is selected.

## 6. Cockpit / Executive Dashboard (Epic E4, Phase 1)

### FR-4.1 Single-pane Cockpit (Must)
- **Input:** DORA metrics (FR-3.1), PR velocity (FR-6.1), epic/goal progress (FR-7.2).
- **Processing:** render headline tiles in one view; evaluate each tile against a configurable
  RAG (red/amber/green) threshold; target ≤ 3 s load at ~1M underlying events (NFR).
- **Output:** RAG-annotated tile grid with trend indicators and a freshness timestamp.
- **Business rules:** a tile whose underlying metric is in an "insufficient data" state (FR-3.1)
  renders as such, not as a false green/red.

### FR-4.2 Org → team → individual drill-down (Must)
- **Input:** a tile click or breadcrumb navigation action.
- **Processing:** drill down while preserving active filters (FR-3.4) and time range; gate
  individual-level drill-down on both the viewer's role (FR-8.1) AND the individual's personal
  opt-in (FR-8.2) — both conditions must hold, neither alone is sufficient.
- **Output:** progressively scoped views (org → team → individual) with breadcrumb navigation
  back up.
- **Business rules:** an attempted drill-down into an individual who has not opted in returns an
  explicit "not opted in" state, never partial or inferred individual data.

### FR-4.3 Presentation-ready export (Must)
- **Input:** an export action from any dashboard view, with the currently active filters.
- **Processing:** render a shareable formatted artifact (PDF or slide-deck-ready format)
  reflecting the exact filtered state on screen, including each displayed metric's plain-language
  definition; complete within seconds.
- **Output:** downloadable export file; a `report_exported` event logged with actor, timestamp,
  and export scope (audit trail, FR-8.3).
- **Business rules:** an export can never contain data outside the exporting user's access scope,
  regardless of what filters were applied.

## 7. Administration & Access Control (Epic E8, Phase 1)

### FR-8.1 Role-based access control (Must)
- **Input:** authenticated user session; role assignment (Admin, Engineering Leader, Manager, IC,
  Finance/Read-only).
- **Processing:** enforce each role's default visibility scope server-side on every API response
  (never client-side only); Admin may refine team- or individual-level visibility rules beyond
  the defaults.
- **Output:** scoped API responses; a 403/empty response (not a data leak) for any request
  outside a role's granted scope, including via deep links and export endpoints.
- **Business rules:** an access-scope change takes effect immediately (no cached stale
  permission window) and is written to the audit log (FR-8.3).

### FR-8.2 Opt-in, non-surveillance individual views (Must)
- **Input:** an IC's explicit opt-in action; a Manager/Leader's attempt to view an individual's
  personal view.
- **Processing:** verify no keystroke, idle-time, or activity-surveillance metric is computed or
  derivable anywhere in the system (design invariant, verified by code review checklist, not a
  runtime check); block any individual-view request lacking the subject IC's opt-in.
- **Output:** individual-level metrics, where shown, always display growth-oriented,
  non-ranking language and an inline definition.
- **Business rules:** an opt-in is revocable at any time by the IC, taking effect immediately.

### FR-8.3 Audit trail (Must)
- **Input:** every configuration change, access-scope change, and data export across the
  platform.
- **Processing:** write an append-only record with actor, timestamp, action, and target for each
  such event; retain for ≥ 12 months; restrict audit-log read access to Admin/Security roles.
- **Output:** a queryable, exportable audit log surfaced in the Admin console.
- **Business rules:** the audit log itself is access-controlled and tamper-evident; audit writes
  are never optional or best-effort for in-scope event types.

## 8. Investment Profile (Epic E5, Phase 2)

### FR-5.1 Planned vs. unplanned vs. rework classification (Should)
- **Input:** Jira ticket type/linkage (FR-1.2) correlated with Git commit/PR activity (FR-1.1).
- **Processing:** classify each unit of work into planned, unplanned, or rework buckets using a
  documented, transparent heuristic; work that cannot be classified is placed in an explicit
  "unclassified" bucket rather than forced into one of the three categories.
- **Output:** per-team time-series split across the four buckets with category definitions shown
  inline.
- **Business rules:** the classification heuristic and its known failure modes are documented
  and inspectable by an Admin.

### FR-5.2 Scope-creep trend detection (Should)
- **Input:** FR-5.1 output over time.
- **Processing:** surface a rising unplanned-work trend against a configurable baseline; support
  drill-down from the trend line into the contributing tickets.
- **Output:** trend visualization with drill-down list of contributing work items.

### FR-5.3 Cost/time attribution for finance (Could)
- **Input:** FR-5.1 output, contributor time allocation, configurable cost rates.
- **Processing:** attribute estimated effort/cost to epics/initiatives using a documented
  methodology.
- **Output:** exportable cost-attribution report suitable for audit review.
- **Business rules:** methodology assumptions are visible and adjustable, never a hidden
  black-box calculation.

## 9. Code Review & PR Analytics (Epic E6, Phase 2)

### FR-6.1 Review analytics (Should)
- **Input:** PR events (FR-1.1) — size (lines changed), open/review/merge timestamps, reviewer
  assignments.
- **Processing:** compute PR size distribution, review turnaround time, and reviewer workload
  balance per team/repo.
- **Output:** distribution and balance charts, always framed as team-level *balance*, never as an
  individual leaderboard or ranking.
- **Business rules:** access scope (FR-8.1) and non-surveillance principles (FR-8.2) apply
  identically to this module — no per-individual ranking view exists regardless of role.

### FR-6.2 Proactive PR flags (Should)
- **Input:** PR age and size against team-configurable thresholds (sensible defaults provided).
- **Processing:** flag PRs exceeding either threshold; surface flags in-product to the relevant
  team.
- **Output:** flagged-PR list; a `pr_flag_actioned` event logged when a user acts on a flag.
- **Business rules:** thresholds are configurable per team, not globally fixed.

## 10. Goals & OKR Tracking (Epic E7, Phase 2)

### FR-7.1 Set metric-linked goals (Should)
- **Input:** a metric selection, target value, scope (team/org), and time window from an
  authorized user.
- **Processing:** bind the goal to a live metric; support multiple concurrent goals per team;
  log a `goal_created` event.
- **Output:** a goal record visible to in-scope roles.

### FR-7.2 Automatic progress tracking (Should)
- **Input:** FR-7.1 goal records; live metric values (refreshed per FR-3.1's freshness target).
- **Processing:** recompute progress on every metric refresh; classify status as
  on-track/at-risk/off-track against the goal's target trajectory.
- **Output:** progress status visible in the Cockpit (FR-4.1) for in-scope leaders; a
  `goal_progress_viewed` event logged on view.
- **Business rules:** progress status is always derived from live data — never manually
  overridden without an audited reason.

## 11. AI Adoption & ROI (Epic E9, Phase 3)

### FR-9.1 Track AI-assisted work (Should)
- **Input:** AI-assistance attribution from FR-1.7.
- **Processing:** aggregate AI-assisted vs. non-AI-assisted work per team over time; carry
  forward the confidence label from FR-1.7.
- **Output:** adoption-rate trend per team; low-confidence attributions visually distinguished
  from telemetry-confirmed ones.

### FR-9.2 AI vs. non-AI delta comparison (Should)
- **Input:** FR-9.1 classification; cycle-time, defect-rate, and rework metrics (FR-3.1, FR-3.2,
  FR-5.1).
- **Processing:** compare AI-assisted vs. non-AI-assisted work on cycle time, defect rate, and
  rework; document known confounders (e.g. task complexity) and control for them where feasible.
- **Output:** side-by-side comparison view with an explicit methodology/limitations note.

### FR-9.3 Dollar ROI figure (Should)
- **Input:** FR-9.2 deltas; assistant licensing cost; contributor cost rate (configurable).
- **Processing:** compute a dollar ROI figure for at least one AI assistant; surface calculation
  assumptions as adjustable inputs; identify high-value usage patterns and low-adoption pockets.
- **Output:** an AI ROI Financial Report (Finance-facing) and an in-product ROI summary tile;
  `ai_roi_report_viewed` event logged.
- **Business rules:** every assumption feeding the dollar figure is visible and editable by an
  authorized user — the figure is never presented as an unexplained black box.

## 12. AI Code Review Agent (Epic E10, Phase 3)

### FR-10.1 First-pass automated review (Could)
- **Input:** a PR-open event on a repository with the agent enabled.
- **Processing:** generate advisory comments covering style, likely bugs, and security concerns;
  post them clearly attributed to the agent, not a human reviewer; this is the platform's sole
  permitted write-back to a connected tool (BRD non-negotiable rule 3 exception).
- **Output:** advisory PR comments; enable/disable toggle per repository.
- **Business rules:** the agent's comments are always advisory — a human remains the merge
  decision-maker in every case; the agent never approves, blocks, or merges a PR itself.

### FR-10.2 Review-quality feedback loop (Could)
- **Input:** review-time and defect-leakage data before and after agent enablement on a repo;
  human feedback on agent comments (useful/not useful).
- **Processing:** compute before/after review-time and defect-leakage deltas; track the agent's
  false-positive rate from human feedback.
- **Output:** a per-repo agent-effectiveness report.

## 13. Custom Reporting & Query Layer (Epic E11, Phase 3)

### FR-11.1 Pre-built report library (Should)
- **Input:** a report selection from a library covering common use cases (cycle time, bug
  resolution, DORA workflow).
- **Processing:** apply the viewer's active filters (FR-3.4) and access scope (FR-8.1) to the
  selected report template.
- **Output:** a filterable, exportable report; no query language required to use it.

### FR-11.2 Guided custom-metric builder (Could)
- **Input:** metric components (data source, aggregation, filter conditions) selected through a
  guided UI.
- **Processing:** compose a custom metric without requiring raw query syntax; validate the
  composed metric against the viewer's access scope before saving.
- **Output:** a saveable, shareable (within scope) custom metric; an optional advanced/raw mode
  may exist for power users but is never required for core use.

## 14. Cross-cutting non-functional constraints on every function above

Every FR in this document is additionally bound by the non-functional requirements in BRD §9 /
PRD §10: dashboards render in < 3 s at ~1M events; metrics refresh within 15 minutes of the source
event; ingestion scales horizontally to 10,000+ contributors; all data is encrypted in transit and
at rest with least-privilege API scopes; the platform sustains 99.5% uptime with graceful
ingestion degradation during vendor outages; and every function must be achievable with zero
change to how an engineer does their day-to-day work.

## 15. Traceability summary

| FR range | BRD source | PRD epic | Status (see PRD Appendix B for detail) |
|---|---|---|---|
| FR-1.1 – FR-1.8 | §8.1, §10 | E1 | Phase 1 — partial, in progress |
| FR-2.1 – FR-2.2 | §8.1 (identity/team), §11.1 | E2 | Phase 1 — partial, in progress |
| FR-3.1 – FR-3.4 | §8.2 | E3 | Phase 1 — partial, in progress |
| FR-4.1 – FR-4.3 | §8.3 | E4 | Phase 1 — partial, in progress |
| FR-5.1 – FR-5.3 | §8.4 | E5 | Phase 2 — not started |
| FR-6.1 – FR-6.2 | §8.5 | E6 | Phase 2 — not started |
| FR-7.1 – FR-7.2 | §8.7 | E7 | Phase 2 — not started |
| FR-8.1 – FR-8.3 | §8.8, §9 | E8 | Phase 1 — partial, in progress |
| FR-9.1 – FR-9.3 | §8.6 | E9 | Phase 3 — not started |
| FR-10.1 – FR-10.2 | §8.5 (P3 agent) | E10 | Phase 3 — not started |
| FR-11.1 – FR-11.2 | §8.9 | E11 | Phase 3 — not started |
