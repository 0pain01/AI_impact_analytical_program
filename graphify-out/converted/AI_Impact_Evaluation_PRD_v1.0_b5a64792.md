<!-- converted from AI_Impact_Evaluation_PRD_v1.0.docx -->


PRODUCT REQUIREMENTS DOCUMENT

Mallify
Engineering Intelligence & Analytics Platform
An AI-native platform that turns fragmented delivery data into trustworthy, board-ready engineering insight.

# Document Control
### Version History
### Relationship to Other Documents
This PRD operationalizes the Mallify Business Requirements Document (BRD v1.0). Where the BRD answers why Mallify should exist and what business outcomes it must deliver, this PRD answers what the product does, for whom, and to what acceptance standard. It is the bridge between business intent and technical design.
### Reviewers & Approvers

# Table of Contents

# 1. Product Overview & Vision
## 1.1 Vision
Mallify unifies data from source control, ticketing, CI/CD, code quality, and AI coding assistants into one analytics layer that computes DORA and SPACE-aligned metrics automatically — no manual tagging, no spreadsheet rebuilds — and presents it through role-based dashboards for executives, managers, and individual contributors. It measures AI coding-assistant adoption and ROI in financial terms, and does so without ever becoming a surveillance tool.
## 1.2 Product Summary
Mallify is a multi-tenant web platform. Teams connect their existing tools once; Mallify ingests, normalizes identities and team structures across those tools, computes metrics on a materialized semantic layer, and surfaces them as drill-down dashboards, exportable executive reports, and (from Phase 3) AI ROI and automated first-pass code review. It is an analytics layer over the existing toolchain — never a replacement for Jira, GitHub, or GitLab.
## 1.3 Product Goals
## 1.4 Non-Goals
- Mallify is not a project-management or Git-hosting replacement; it reads from those systems, it does not replace them.
- Mallify does not track individual keystrokes, idle time, or any surveillance-style signal — this is a deliberate product boundary, not a backlog gap.
- Mallify does not generate HR performance-review documents (deferred beyond Phase 3).
- Mallify does not require engineers to change how they work to produce value.
## 1.5 Guiding Product Principles
# 2. Product Strategy & Positioning
## 2.1 Positioning: the deliberate middle ground
Mallify does not clone either reference product. It occupies the gap between them: the automated, no-tagging-required DORA/SPACE analytics of minware, delivered through the accessible, leadership-friendly storytelling UX of Hivel — while explicitly avoiding the individual-surveillance features that both the market and academic research flag as trust-eroding.
## 2.2 Deployment & commercialization posture
Mallify is built internal-first but commercialization-ready. Phase 1 ships as SaaS multi-tenant for internal pilot teams; the architecture must not preclude later external onboarding, VPC/on-prem ingest, or SOC 2 Type II certification. The internal-tool-versus-commercial-product decision is an open business decision (see Section 16) — this PRD keeps both paths open and flags the small number of requirements that differ between them.
# 3. Personas & Jobs-to-be-Done
Seven personas drive Mallify's design. Each row states the job the persona is trying to get done, the primary surface that serves it, and the access posture that governs what they can see.
## 3.1 Persona summary
## 3.2 Persona detail & success moment
### CTO / VP Engineering
Job-to-be-done:  Walk into a board meeting able to defend engineering headcount, tooling spend, and AI investment with objective numbers.
Pain today:  Rebuilds the same slides every quarter from spreadsheets and gut feel; cannot prove AI ROI.
Success moment:  Opens the Executive Cockpit, sees green/amber/red DORA status and a dollar AI-ROI figure, and exports a board-ready report in under two minutes.
### Engineering Manager
Job-to-be-done:  Spot the real bottleneck this sprint and walk into 1:1s with fair, data-grounded context.
Pain today:  Bottlenecks are hidden across Git and Jira; 1:1 prep is anecdotal.
Success moment:  Filters the Team Dashboard to this sprint, sees review turnaround is the constraint, and drills into the specific stalled PRs — without any individual being ranked or shamed.
### Tech Lead / Senior Engineer
Job-to-be-done:  Keep the team's review pipeline flowing and catch quality drift before it ships.
Pain today:  Oversized PRs and stale reviews are noticed only after they cause delay.
Success moment:  Gets flagged on an aging, oversized PR proactively and rebalances reviewer load before it blocks the release.
### Individual Contributor
Job-to-be-done:  Understand my own contribution trends on my terms, framed around growth.
Pain today:  Fears being surveilled; has no transparent view of how work is measured.
Success moment:  Opts into a personal view, sees clear metric definitions and their own trend, and trusts that no manager is watching keystrokes.
### Product / Program Manager
Job-to-be-done:  Predict whether the roadmap will land and catch scope creep early.
Pain today:  Planned-versus-unplanned work is invisible until a deadline slips.
Success moment:  Sees the Investment Profile show unplanned work rising for a team and raises it before the milestone is at risk.
### Finance / Procurement
Job-to-be-done:  Decide, with evidence, whether AI tooling and Mallify itself are worth their cost.
Pain today:  AI-tool spend is a line item with no measurable return.
Success moment:  Opens the AI ROI Financial Report and sees cost-savings, quality-delta, and throughput-delta attributed to specific AI tooling.
### Security / Compliance
Job-to-be-done:  Guarantee that access is least-privilege and every data touch is auditable.
Pain today:  No central audit trail for who saw which engineering data.
Success moment:  Reviews the Admin console, confirms role scopes, and retrieves a 12-month audit log of access and configuration changes on demand.
# 4. Success Metrics & Instrumentation
## 4.1 North Star
## 4.2 Product KPIs (platform-level)
## 4.3 Instrumentation events (product analytics)
The product must emit these first-party events so KPIs above are measurable from day one. Events are aggregate/behavioral and contain no surveillance content.
# 5. Epic Map & Requirement Traceability
Requirements are organized into eleven epics. Each epic maps back to BRD functional modules and forward to a delivery phase, so every story is traceable from business objective to release. Detailed stories and acceptance criteria follow in Section 6.
# 6. Detailed Requirements by Epic
Each epic below states its goal, the key functional behavior expected, and the user stories that define done. Acceptance criteria are the contract QA and engineering build to; they are intentionally testable.
## E1 · Onboarding & Connectors
Goal: an admin connects the organization's tools in minutes, over secure least-privilege scopes, and reaches first insight in under 30 minutes. This epic owns the integration funnel that every other metric depends on.
### Functional detail
- Connector types at MVP: Git host (GitHub and/or GitLab/Bitbucket), ticketing (Jira), one CI/CD tool (Jenkins / GitHub Actions / Azure Pipelines). Should-have: code-quality (SonarQube), incident (PagerDuty/Opsgenie), AI-assistant telemetry.
- Auth via OAuth / app-installation with least-privilege scopes; secrets stored encrypted; per-connector health status surfaced to admins.
- Ingestion is resilient: retries, queueing, and graceful degradation on vendor API errors, rate limits, or schema changes — never silent data loss.
E1-S1  Connect a Git provider   [Must]  ·  Phase 1
As an Admin, I want to connect GitHub/GitLab/Bitbucket via OAuth or app install so that Mallify can ingest commits, PRs, reviews, and branches.
Acceptance criteria
- Given valid credentials, when I authorize the connection, then only least-privilege read scopes are requested and the connection status shows Connected.
- Backfill of historical repository data begins automatically and its progress is visible.
- If authorization fails or is revoked, the connector shows an actionable error, not a silent failure.
- Selected repositories/orgs can be scoped in or out before ingestion begins.
E1-S2  Connect ticketing (Jira)   [Must]  ·  Phase 1
As an Admin, I want to connect Jira (or equivalent) to ingest epics, stories, sprints, and status transitions so that delivery metrics can correlate code with planned work.
Acceptance criteria
- Epics, stories, sprints, and status-change history ingest and are queryable within the freshness target.
- Projects can be mapped to Mallify teams during setup.
- Missing or non-standard workflows are tolerated and reported, not rejected.
E1-S3  Connect a CI/CD tool   [Must]  ·  Phase 1
As an Admin, I want to connect one CI/CD system to ingest build and deployment events so that Deployment Frequency and Change Failure Rate can be computed.
Acceptance criteria
- Build and deployment events with timestamps and outcomes ingest reliably.
- Deployment events can be mapped to repositories/services for DORA attribution.
- Pipeline duration is captured for delivery-stage breakdowns.
E1-S4  Resilient ingestion   [Must]  ·  Phase 1
As a platform operator, I want ingestion to tolerate vendor API errors, rate limits, and schema changes so that no data is lost and metrics stay trustworthy during upstream issues.
Acceptance criteria
- Transient errors are retried with backoff; persistent failures raise a connector-health alert.
- Rate limits are respected without dropping events (queue-and-drain).
- An unexpected upstream schema change is logged and quarantined rather than corrupting computed metrics.
E1-S5  30-minute time-to-value   [Must]  ·  Phase 1
As a first-time Admin, I want to reach a meaningful populated dashboard within 30 minutes of connecting tools so that the platform proves value immediately.
Acceptance criteria
- After the first Git + ticketing connection, at least one populated dashboard renders without manual configuration.
- A guided setup checklist shows remaining steps and current data-readiness.
- The connect→first-render duration is instrumented against the 30-minute target.
## E2 · Identity & Team Normalization
Goal: reconcile that one human is 'v.sharma' in Git, 'Vishal S' in Jira, and 'vishal@' in CI, and that people roll up into teams and sub-teams — so every metric attributes correctly. Inaccurate identity resolution is the fastest way to lose leadership trust, so this epic is foundational.
E2-S1  Contributor identity reconciliation   [Must]  ·  Phase 1
As the platform, I want to merge a contributor's identities across tools by name, email, and user-ID heuristics so that activity is attributed to one person, not several ghosts.
Acceptance criteria
- Given multiple tool identities that resolve to the same person, when normalization runs, then metrics attribute to a single canonical contributor.
- An Admin can review, confirm, merge, or split suggested identity matches.
- Unresolved identities are flagged rather than silently dropped or double-counted.
E2-S2  Team-structure import   [Should]  ·  Phase 1
As an Admin, I want to import multi-level org > team > sub-team structure from a connected source so that metrics can roll up and drill down along the real org shape.
Acceptance criteria
- A three-level hierarchy imports and is editable in Mallify.
- Contributors can belong to a team; reassignments recompute historical roll-ups consistently.
- Teams with no source-of-record can be created manually.
## E3 · DORA & Delivery Metrics
Goal: compute the four DORA metrics plus lead-time breakdowns automatically, with zero manual tagging, filterable by team, repo, sprint, and date range. This is the analytical heart of the MVP.
### Functional detail
- DORA metrics: Deployment Frequency, Lead Time for Changes, Change Failure Rate, Mean Time to Restore.
- Lead-time stage breakdown: first commit → PR open → review → merge → deploy.
- Ticket Lead Time (multi-PR feature completion) shown alongside PR-level lead time.
- Change Failure Rate derived from incident linkage, hotfixes, or rollback events.
E3-S1  Automated DORA computation   [Must]  ·  Phase 1
As an Engineering Manager, I want the four DORA metrics computed automatically from tool data so that I never again maintain them by hand.
Acceptance criteria
- All four DORA metrics compute without any manual tagging or status discipline from engineers.
- Each metric shows current value, trend, and comparison to a configurable target band.
- Every metric exposes a plain-language definition of how it is calculated.
E3-S2  Lead-time stage breakdown   [Must]  ·  Phase 1
As an Engineering Manager, I want lead time broken into commit → PR → review → merge → deploy stages so that I can see exactly where delivery stalls.
Acceptance criteria
- The breakdown renders per team and per repo for a chosen period.
- The slowest stage is visually emphasized so the bottleneck is obvious at a glance.
- Ticket-level lead time is available alongside PR-level lead time.
E3-S3  Change Failure Rate from incidents   [Should]  ·  Phase 1
As an Engineering Leader, I want CFR computed from incidents, hotfixes, and rollbacks so that quality is measured, not guessed.
Acceptance criteria
- Deployments linked to a subsequent incident/hotfix/rollback are counted as failures.
- The linkage logic is transparent and adjustable by an Admin.
- MTTR is derived from incident open/resolve timestamps.
E3-S4  Universal filtering   [Must]  ·  Phase 1
As any analytical user, I want to filter every metric by team, repository, sprint, and custom date range so that I can answer specific questions, not just see org averages.
Acceptance criteria
- Filters apply consistently across all metric views and persist within a session.
- Filtered results respect the viewer's access scope.
- An empty result set explains why (e.g., no data in range) rather than showing a blank chart.
## E4 · Cockpit / Executive Dashboard
Goal: a single-pane executive view combining DORA, PR velocity, and epic progress, with red-amber-green status against targets, drill-down from org to team to individual (access-permitting), and one-click export for leadership reviews.
E4-S1  Single-pane Cockpit   [Must]  ·  Phase 1
As a CTO / VP Engineering, I want one dashboard combining DORA, PR velocity, and epic progress so that I have board-ready visibility without assembling it myself.
Acceptance criteria
- The Cockpit renders headline DORA metrics, PR velocity, and epic progress on one screen.
- Each tile shows red/amber/green status against configurable thresholds.
- The full view loads within the 3-second performance target for datasets up to ~1M events.
E4-S2  Org → team → IC drill-down   [Must]  ·  Phase 1
As an Engineering Leader, I want to drill from org level to team to individual so that I can trace a headline number to its source.
Acceptance criteria
- Drill-down preserves the active filters and time range.
- Individual-level drill-down is gated by access control and personal opt-in (see E8).
- Breadcrumbs let me navigate back up the hierarchy.
E4-S3  Presentation-ready export   [Must]  ·  Phase 1
As a CTO / VP Engineering, I want to export the current view as a presentation-ready report so that I stop rebuilding slides every quarter.
Acceptance criteria
- Export produces a shareable, formatted artifact (PDF/deck-ready) reflecting current filters.
- Export completes in seconds and is logged as a report_exported event.
- Metric definitions accompany the export so numbers are not misread out of context.
## E5 · Investment Profile
Goal: merge Jira and Git activity to classify engineering time as planned (epic-aligned), unplanned, or rework, surface scope-creep trends, and provide cost/time attribution suitable for engineering-cost capitalization.
E5-S1  Planned vs unplanned vs rework   [Should]  ·  Phase 2
As a Product / Program Manager, I want engineering time classified as planned, unplanned, or rework so that I can see where effort actually goes.
Acceptance criteria
- Git activity is correlated to Jira epics to classify each unit of work.
- The split is shown per team over time with clear category definitions.
- Unclassifiable work is bucketed transparently, not hidden.
E5-S2  Scope-creep trend   [Should]  ·  Phase 2
As a Program Manager, I want to see unplanned-work and scope-change trends over time so that I can raise risk before a milestone slips.
Acceptance criteria
- A rising unplanned-work trend for a team is visually surfaced.
- I can drill from the trend into the contributing tickets/work.
E5-S3  Cost/time attribution for finance   [Could]  ·  Phase 2
As Finance, I want cost/time attribution suitable for engineering-cost capitalization so that capitalizable engineering effort is auditable.
Acceptance criteria
- Effort can be attributed to epics/initiatives with time-based cost estimates.
- The attribution methodology is documented and exportable for audit.
## E6 · Code Review & PR Analytics
Goal: make the review pipeline observable — PR size distribution, review turnaround, and reviewer-load balance — and proactively flag oversized PRs and stale reviews before they block a release.
E6-S1  Review analytics   [Should]  ·  Phase 2
As a Tech Lead, I want PR size distribution, review turnaround time, and reviewer workload balance so that I can keep the review pipeline healthy.
Acceptance criteria
- All three views render per team and per repo for a chosen period.
- Reviewer load is shown as balance, never as an individual leaderboard.
- Metrics respect access scope and privacy principles.
E6-S2  Proactive PR flags   [Should]  ·  Phase 2
As a Tech Lead, I want oversized PRs and stale/aging reviews flagged proactively so that I act before they cause delay.
Acceptance criteria
- PRs exceeding configurable size/age thresholds are flagged in-product.
- Acting on a flag is captured as a pr_flag_actioned event.
- Thresholds are team-configurable, with sensible defaults.
## E7 · Goals & OKR Tracking
Goal: let engineering leaders set quarterly targets tied to live metrics (e.g., 'reduce lead time by 20%') and track progress automatically, with no manual status updates.
E7-S1  Set metric-linked goals   [Should]  ·  Phase 2
As an Engineering Leader, I want to set a quarterly team/org goal tied to a live metric so that targets are grounded in real data rather than aspiration.
Acceptance criteria
- A goal binds to a specific metric, target value, scope, and time window.
- Multiple concurrent goals per team are supported.
- Goal creation emits a goal_created event.
E7-S2  Automatic progress tracking   [Should]  ·  Phase 2
As an Engineering Leader, I want progress against goals to update automatically so that I never chase manual status updates.
Acceptance criteria
- Progress recomputes as underlying metrics refresh, within the freshness target.
- On-track / at-risk / off-track status is shown against the target trajectory.
- Goal progress is visible in the Cockpit for in-scope leaders.
## E8 · Administration & Access Control
Goal: enforce least-privilege, role-based access; make individual visibility opt-in and non-punitive by design; and maintain a full audit trail. This epic encodes the trust-over-surveillance principle into the access model itself.
### Roles
E8-S1  Role-based access control   [Must]  ·  Phase 1
As a Security / Compliance lead, I want role-based access with configurable team and individual visibility rules so that people see only what their role permits.
Acceptance criteria
- Each of the five roles enforces its default visibility; Admin can refine team/individual rules.
- A user cannot drill into data outside their scope via any path, including exports and deep links.
- Access changes take effect immediately and are logged.
E8-S2  Opt-in, non-surveillance individual views   [Must]  ·  Phase 1
As an Individual Contributor, I want my personal view to be opt-in and framed around growth, never surveillance so that I trust the platform.
Acceptance criteria
- No keystroke, idle-time, or activity-surveillance metric exists anywhere in the product.
- A manager cannot view an individual's personal view unless the IC has opted in per applicable policy.
- Every individual-level metric displays its definition and is presented in growth-oriented, non-ranking language.
E8-S3  Audit trail   [Must]  ·  Phase 1
As a Security / Compliance lead, I want a full audit log of configuration changes, access grants, and data exports so that I can demonstrate governance on demand.
Acceptance criteria
- All configuration changes, access grants, and exports are logged with actor, timestamp, and target.
- Logs are retrievable for at least 12 months.
- The audit log is itself access-controlled and tamper-evident.
## E9 · AI Adoption & ROI  (Phase 3)
Goal: measure AI coding-assistant adoption and convert its productivity effect into a defensible dollar figure — the capability most directly tied to board-level pressure and to Mallify's differentiation.
E9-S1  Track AI-assisted work   [Should]  ·  Phase 3
As an Engineering Leader, I want to identify AI-assisted commits/PRs from telemetry or conventions so that adoption is measured objectively, not by self-report.
Acceptance criteria
- AI-assisted work is detected from assistant telemetry and/or commit conventions where available.
- Detection confidence and method are transparent; low-confidence attribution is labeled.
- Adoption is shown per team over time.
E9-S2  AI vs non-AI delta   [Should]  ·  Phase 3
As Finance, I want cycle time, defect rate, and rework compared between AI-assisted and non-AI work so that I can see the real quality and speed effect.
Acceptance criteria
- The three deltas render side by side with clear methodology.
- Comparisons control for obvious confounders where feasible and state their limits.
E9-S3  Dollar ROI figure   [Should]  ·  Phase 3
As a CTO / Finance, I want productivity deltas converted into an estimated dollar ROI so that AI tooling spend can be defended to the board.
Acceptance criteria
- At least one AI assistant yields a dollar ROI figure in the first Phase-3 release.
- The ROI calculation's assumptions are visible and adjustable.
- High-value usage patterns and low-adoption pockets are surfaced for enablement.
## E10 · AI Code Review Agent  (Phase 3)
Goal: an automated first-pass reviewer that comments on style, potential bugs, and security issues before human review — raising baseline quality and freeing senior reviewers for judgment-level work.
E10-S1  First-pass automated review   [Could]  ·  Phase 3
As a Tech Lead, I want an automated first-pass review on new PRs so that obvious issues are caught before human reviewers engage.
Acceptance criteria
- On PR open, the agent posts comments covering style, likely bugs, and security concerns.
- Comments are advisory and clearly attributed to the agent; humans remain the deciders.
- Teams can enable/disable the agent per repository.
E10-S2  Review quality feedback loop   [Could]  ·  Phase 3
As an Engineering Leader, I want to see the agent's impact on review time and defect leakage so that I can judge whether it earns its place.
Acceptance criteria
- Before/after review-time and defect-leakage effects are reported.
- False-positive rate is tracked so the agent's usefulness is honest.
## E11 · Custom Reporting & Query Layer  (Phase 3)
Goal: serve 90% of users with a pre-built report library out of the box, and give power users a guided custom-metric builder — deliberately avoiding a mandatory SQL-like query language for everyday use, which directly addresses the steep-learning-curve criticism of minware's minQL.
E11-S1  Pre-built report library   [Should]  ·  Phase 3
As any analytical user, I want a library of pre-built reports (cycle time, bug resolution, DORA workflow) so that I get common answers without building anything.
Acceptance criteria
- The library covers the most common use cases out of the box.
- Reports are filterable and exportable like every other surface.
- No query language is required to use any pre-built report.
E11-S2  Guided custom-metric builder   [Could]  ·  Phase 3
As a technically capable user, I want a guided builder to define custom metrics so that I get depth without needing SQL literacy.
Acceptance criteria
- A user can compose a custom metric through a guided UI, not raw query text, for everyday cases.
- Custom metrics can be saved, shared within scope, and exported.
- An optional advanced/raw mode may exist but is never required for core use.
# 7. Key User Flows
Three flows carry most of Mallify's value. Each is described as an ordered path with the success condition that defines it.
## 7.1 First-run: connect to first insight (Admin)
- Admin signs in and lands on a guided setup checklist showing required and optional connectors.
- Admin connects a Git provider (OAuth, least-privilege). Historical backfill starts with visible progress.
- Admin connects Jira and one CI/CD tool; projects and deployments are mapped to teams.
- Mallify normalizes identities and team structure; unresolved matches are flagged for optional review.
- A first populated dashboard renders. The connect→render duration is checked against the 30-minute target.
## 7.2 Board prep: from Cockpit to exported report (Exec)
- Exec opens the Executive Cockpit; DORA, PR velocity, and epic progress show with RAG status.
- Exec spots an amber Lead-Time tile and drills org → team to find the slow stage.
- Exec applies a quarter filter and confirms the trend and any linked goal's status.
- Exec exports a presentation-ready report; metric definitions are attached automatically.
## 7.3 Sprint health: bottleneck to action (Manager / Tech Lead)
- Manager filters the Team Dashboard to the current sprint.
- Lead-time breakdown highlights review as the slowest stage.
- Manager opens Code Review Analytics; a stale, oversized PR is flagged.
- Manager rebalances reviewer load and follows up on the flagged PR (captured as pr_flag_actioned).
# 8. Information Architecture & Navigation
Primary navigation is role-aware: a user sees only the surfaces their role and scope permit. The structure below is the full map; individual roles see a subset.
# 9. UX & Design Requirements
- Insight-first layout: every screen leads with the answer (status, trend), with raw detail one drill-down away — never an unlabeled data dump.
- Definitions on demand: every metric exposes a plain-language definition inline, reinforcing trust and preventing misreading.
- Consistent RAG semantics: red/amber/green means the same thing everywhere and is always relative to a stated, configurable target.
- Filters persist and travel: team, repo, sprint, and date-range filters carry through drill-downs and exports within a session.
- Growth-framed individual UX: personal views use non-ranking, non-punitive language and never appear as leaderboards.
- Accessible & responsive: WCAG-conscious contrast and keyboard navigation; dashboards remain legible on laptop and large display.
- Empty and loading states explain themselves: a blank chart states why (no data / out of range / still ingesting), never just renders empty.
# 10. Non-Functional Requirements
# 11. Data & Privacy Requirements
Mallify reads from the sources below over least-privilege scopes. Privacy posture is a product requirement, not a policy afterthought.
### Privacy guarantees (must hold across all data)
- No keystroke, idle-time, or activity-surveillance data is collected or derivable — full stop.
- Individual-level views are opt-in and access-gated; aggregate/team views are the default.
- Metric definitions are always transparent so no number can be weaponized out of context.
- Data residency needs (VPC/on-prem ingest) may be required earlier than Phase 3 for specific engagements.
# 12. Release Plan & Milestones
Releases map to the BRD phasing. Durations are indicative and to be validated by the Technical Architect and Delivery Lead in Phase 0. Each release is defined by the epics it completes.
### MVP definition (Phase 1 exit)
# 13. Dependencies
# 14. Assumptions & Constraints
## 14.1 Assumptions
- Target teams already use a supported Git host and ticketing tool; Mallify is an analytics layer, not a replacement.
- Engineering leadership sponsorship exists to encourage adoption across pilot teams.
- A pilot group of 2–3 teams is available for Phase 1 validation before broader rollout.
- Where AI assistants are used, basic telemetry or commit conventions exist to identify AI-assisted work.
## 14.2 Constraints
- Build-vs-buy budget and headcount are not yet formally approved.
- Data-residency or client-contractual needs may mandate VPC/on-prem earlier than Phase 3.
- Third-party API rate limits constrain ingestion frequency and near-real-time refresh targets.
# 15. Out of Scope
- Individual developer surveillance (keystroke/idle-time tracking) — permanently excluded on trust and ethics grounds.
- Replacing existing PM or Git-hosting tools — Mallify layers on top, it does not replace Jira/GitHub/GitLab.
- Full performance-review automation (generating HR review documents) — deferred beyond Phase 3.
# 16. Open Questions & Decisions Needed
These must be resolved to close Phase 0. Each has an owner and a blocking relationship.
# Appendix A · Metric & Term Glossary
Shared definitions so every stakeholder reads Mallify numbers the same way. These definitions must appear inline in-product wherever the corresponding metric is shown.

# Approval
The undersigned confirm review and approval of this Product Requirements Document, authorizing progression to technical design (C4 model) and backlog creation.

| Field | Detail |
| --- | --- |
| Document | Product Requirements Document (PRD) v1.0 — Draft for Review |
| Product | Mallify — Engineering Intelligence & Analytics Platform |
| Prepared by | Vishal & Aditi |
| Prepared for | Product Owner · Engineering Sponsor · Technical Architect |
| Status | Draft — pending BRD sign-off (BRD v1.0) |
| Date | July 5, 2026 |
| Source | Derived from Mallify BRD v1.0 (Hivel & minware competitive analysis) |
| Version | Date | Author | Description |
| --- | --- | --- | --- |
| 0.1 | 04-Jul-2026 | Vishal, Aditi | PRD skeleton drafted alongside BRD |
| 1.0 | 05-Jul-2026 | Vishal, Aditi | Full PRD derived from approved BRD scope for stakeholder review |
| 1.1 | TBD | Product Owner | Post-review updates following steering committee feedback |
| Document | Owner | Status / Dependency |
| --- | --- | --- |
| Business Requirements Document (BRD) | Product + Sponsor | v1.0 — upstream input to this PRD |
| Product Requirements Document (this) | Product Owner | v1.0 — current document |
| C4 Architecture Model | Technical Architect | To follow PRD sign-off |
| Security & Data Privacy Assessment | Security / Compliance | Parallel to technical design |
| UX Design & Prototype | Design | Derived from Sections 7–9 of this PRD |
| Engineering Delivery Plan / Backlog | Delivery Lead | Epics & stories in Section 6 seed the backlog |
| Role | Responsibility on this PRD | Sign-off Required |
| --- | --- | --- |
| Engineering Sponsor | Confirms product intent matches business case | Yes |
| Product Owner | Owns scope, priority, and acceptance | Yes (author) |
| Technical Architect | Confirms feasibility; inherits into C4 model | Yes |
| QA / Security Lead | Confirms testability, privacy, access model | Yes |
| Design Lead | Confirms UX direction is buildable | Advisory |
| Give every engineering leader a single, trustworthy, real-time answer to: “How healthy is our software delivery, and what is our AI investment returning?” |
| --- |
| Goal | Description | Tied to BRD Objective |
| --- | --- | --- |
| PG-1 | One source of truth for delivery performance across all teams | BO-1 |
| PG-2 | Fully automated DORA + SPACE-aligned metrics with no manual status updates | BO-2 |
| PG-3 | Quantify AI coding-assistant adoption and ROI in dollars | BO-3 |
| PG-4 | Reduce code-review cycle time and defect leakage | BO-4 |
| PG-5 | Make planned-vs-unplanned engineering time visible (Investment Profile) | BO-5 |
| PG-6 | Role-based views tuned to Exec, Manager, and IC needs | BO-6 |
| PG-7 | Be commercialization-ready without re-architecture (optional path) | BO-7 |
| Principle | What it means for the product |
| --- | --- |
| Trust over surveillance | Individual views are opt-in and growth-framed. No metric may rank or shame an individual by default. Metric definitions are always visible. |
| Insight, not raw data | Every screen answers a question a leader actually asks. Numbers arrive with context, thresholds, and trend — never as an unlabeled dump. |
| Zero-tagging automation | Metrics are derived from tool events, not from engineers remembering to update statuses. Garbage-in-garbage-out is designed out. |
| Fast time-to-value | First meaningful dashboard within 30 minutes of connecting tools. No query language required for core use. |
| Accessible depth | Pre-built reports serve 90% of users out of the box; a guided custom-metric builder serves power users without forcing SQL literacy. |
| Resilient by design | A single vendor API outage degrades gracefully and never loses data or corrupts a metric. |
| Dimension | Hivel | minware | Mallify |
| --- | --- | --- | --- |
| Core stance | Ease of use; exec storytelling | Deep customization (minQL) | Out-of-box insight + optional depth |
| DORA | Automated | Automated, ticket-level | Automated, no tagging |
| AI ROI | Strong ($ module) | Text-classification based | $ ROI + adoption + quality delta (Ph.3) |
| Custom queries | Limited | Deep, SQL-like | Pre-built + guided builder |
| Key risk avoided | Surveillance if misconfigured | Steep learning curve | Avoids both by design |
| Open decision carried from the BRD: Is Mallify an internal platform, a commercial SaaS, or both? Tracked in Section 16. Phase 1–2 requirements are identical for either path; commercialization-specific work is isolated in Phase 4. |
| --- |
| Persona | Primary job-to-be-done | Primary surface | Access |
| --- | --- | --- | --- |
| CTO / VP Engineering | Report delivery speed, quality & AI ROI to the board with confidence | Executive Cockpit, AI ROI Summary | Org-wide |
| Engineering Manager | Find bottlenecks, balance workload, prep 1:1s from real data | Team Dashboard, Activity, Investment Profile | Team scope |
| Tech Lead / Senior Eng | Keep review load healthy and code-quality trends positive | Code Review Analytics | Team scope |
| Individual Contributor | See own contribution trends transparently and non-punitively | Personal Activity (opt-in) | Self only |
| Product / Program Mgr | Predict delivery and see scope creep early | Investment Profile, Cycle-Time reports | Team scope |
| Finance / Procurement | Judge cost-benefit of AI tooling and of Mallify itself | AI ROI Financial Report | Read-only, aggregated |
| Security / Compliance | Govern access, audit data use, enforce privacy | Admin & Access Console | Admin |
| North Star: Weekly leaders acting on Mallify insight
The count of Exec/Manager users who take a tracked action (drill-down, export, goal edit, follow-up on a flagged PR) at least once per week. It captures the real outcome — leaders making decisions from Mallify rather than spreadsheets — better than raw logins. |
| --- |
| KPI | Target | Source / How measured |
| --- | --- | --- |
| Time-to-first-insight | < 30 min from first tool connection | Timestamp: connect event → first dashboard render |
| Manual reporting effort saved | 70%+ reduction within 2 quarters | Manager survey + report-export usage |
| Team adoption | 80%+ of target teams active within 90 days | Active-team instrumentation |
| Data-trust score | ≥ 4 / 5 among engineering leads | In-product survey |
| AI ROI demonstrated | ≥ 1 assistant with a $ figure in first release | AI ROI module output |
| Dashboard load | < 3 s for datasets up to ~1M events | Front-end performance telemetry |
| Metric freshness | Within 15 min of source event | Ingestion → materialization lag metric |
| Event | Why it exists |
| --- | --- |
| connector_connected | Starts the time-to-first-insight clock; tracks integration funnel |
| first_dashboard_rendered | Stops the time-to-first-insight clock |
| dashboard_viewed / drilldown_opened | Feeds North Star + engagement depth |
| report_exported | Proxy for manual-reporting-effort saved |
| goal_created / goal_progress_viewed | OKR-module adoption |
| pr_flag_actioned | Confirms code-review insights drive action |
| ai_roi_report_viewed | AI ROI module value validation |
| survey_response_submitted | Data-trust score capture |
| Epic | Name | BRD source | Phase |
| --- | --- | --- | --- |
| E1 | Onboarding & Connectors | FR-1.x, §5.1, §10 | Phase 1 |
| E2 | Identity & Team Normalization | FR-1.4, FR-1.5, §11.1 | Phase 1 |
| E3 | DORA & Delivery Metrics | §8.2, §5.1 | Phase 1 |
| E4 | Cockpit / Executive Dashboard | §8.3, §5.1 | Phase 1 |
| E5 | Investment Profile | §8.4, §5.1 | Phase 2 |
| E6 | Code Review & PR Analytics | §8.5, §5.1 | Phase 2 |
| E7 | Goals & OKR Tracking | §8.7, §5.2 | Phase 2 |
| E8 | Administration & Access Control | §8.8, §9 | Phase 1 |
| E9 | AI Adoption & ROI | §8.6, §5.2 | Phase 3 |
| E10 | AI Code Review Agent | §8.5, §5.2 | Phase 3 |
| E11 | Custom Reporting & Query Layer | §8.9, §5.2 | Phase 3 |
| MoSCoW priority is noted on every story: Must (MVP-blocking) · Should (high value, not MVP-blocking) · Could (desirable, opportunistic). |
| --- |
| Role | Default visibility |
| --- | --- |
| Admin | Full configuration, connectors, roles, audit log |
| Engineering Leader | Org-wide aggregated + team drill-down |
| Manager | Own team(s) only |
| Individual Contributor | Self only, opt-in personal view |
| Finance / Read-only | Aggregated ROI & cost views; no individual data |
| Success condition: A populated DORA/Cockpit view appears within 30 minutes, with no query-writing and no engineer behavior change. |
| --- |
| Success condition: A board-ready, correctly-scoped report is produced in under two minutes without rebuilding slides. |
| --- |
| Success condition: The bottleneck is identified and acted on within the sprint — no individual is ranked or shamed in the process. |
| --- |
| Top-level area | Contains | Primary personas |
| --- | --- | --- |
| Cockpit | Exec DORA + velocity + epic progress, RAG, export | Exec, Leader |
| Teams | Team dashboards, lead-time breakdown, activity | Manager, Leader |
| Code Review | PR analytics, flags, reviewer balance | Tech Lead, Manager |
| Investment | Planned/unplanned/rework, scope-creep, attribution | PM, Finance, Leader |
| Goals | Metric-linked goals & auto progress | Leader, Manager |
| AI Insights (Ph.3) | Adoption, AI vs non-AI delta, $ ROI, review agent | Exec, Finance, Leader |
| Reports (Ph.3) | Pre-built library + guided custom builder | All analytical roles |
| Personal (opt-in) | Own contribution trends, growth-framed | Individual Contributor |
| Admin | Connectors, roles/access, identity, audit log | Admin, Security |
| Category | Requirement |
| --- | --- |
| Performance | Dashboards load in < 3 s for datasets up to ~1M events; metric recomputation is near-real-time (target within 15 min of source event). |
| Scalability | Ingestion workers scale horizontally as repos/teams grow; support 10,000+ contributors over the platform lifetime. |
| Security | SOC 2 Type II readiness as a Phase 2/3 target; encryption in transit and at rest; least-privilege scopes for every integration. |
| Data privacy | No individual surveillance metrics by design; individual views are opt-in and growth-framed. |
| Availability | Target 99.5% uptime for the SaaS control plane; ingestion degrades gracefully (queue & retry) during upstream outages. |
| Usability | Time-to-first-dashboard-value under 30 minutes; no change to how engineers work. |
| Auditability | All config changes, access grants, and exports logged and retrievable for 12+ months. |
| Deployment flexibility | Phase 1 SaaS multi-tenant; Phase 3 VPC / on-prem ingest option for regulated clients. |
| Extensibility | New source connectors can be added without core re-architecture. |
| Source | Example tools | Data extracted |
| --- | --- | --- |
| Source control | GitHub, GitLab, Bitbucket | Commits, branches, PRs, reviews, merges |
| Project management | Jira, Azure Boards, Linear | Epics, stories, sprints, status transitions |
| CI/CD | Jenkins, GitHub Actions, Azure Pipelines | Build & deploy events, pipeline duration |
| Code quality | SonarQube | Code smells, tech debt, coverage, security findings |
| Incident mgmt | PagerDuty, Opsgenie | Incident open/resolve timestamps (MTTR/CFR) |
| AI assistants | Copilot, Cursor, Claude Code | AI-attributed commits/PRs, acceptance signals |
| Communication (opt.) | Slack, calendar | Meeting-load context for SPACE wellbeing signals |
| Phase | Duration | Epics delivered | Milestone / exit criteria |
| --- | --- | --- | --- |
| Phase 0 — Discovery | 2–3 wks | PRD sign-off, C4 model, pilot selection | BRD + PRD approved; pilot teams named; build-vs-buy quotes in |
| Phase 1 — MVP | 8–10 wks | E1, E2, E3, E4, E8 | Git+Jira+1 CI/CD live; DORA + Cockpit + RBAC; 30-min TTV met |
| Phase 2 — Depth | 6–8 wks | E5, E6, E7 (+ code-quality connector) | Investment Profile, PR analytics, Goals shipped to pilot |
| Phase 3 — AI & Advanced | 8–12 wks | E9, E10, E11 (+ VPC/on-prem option) | AI ROI $ figure produced; custom reports; review agent piloted |
| Phase 4 — Commercialize (opt.) | Ongoing | Multi-tenant packaging, SOC 2 Type II | External onboarding ready (only if commercial path chosen) |
| Mallify MVP is done when: a pilot team connects Git + Jira + one CI/CD tool, reaches a populated DORA Cockpit in under 30 minutes, drills org→team with RBAC enforced, and exports a leadership-ready report — with zero manual tagging and zero surveillance metrics. |
| --- |
| Dependency | Impact if unmet |
| --- | --- |
| Third-party API access & scopes (GitHub, Jira, CI/CD) | No ingestion; blocks all metrics |
| Pilot team availability (2–3 teams) | No Phase-1 validation of accuracy or adoption |
| Engineering sponsorship to mandate/encourage adoption | Low adoption undermines KPIs |
| AI-assistant telemetry or commit conventions | AI adoption/ROI (E9) attribution is weaker |
| C4 architecture model (Technical Architect) | Build cannot start with confidence |
| Build-vs-buy decision (Finance/Procurement) | Scope of build vs licensing remains open |
| ID | Question / Decision | Owner | Blocks |
| --- | --- | --- | --- |
| OQ-1 | Is Mallify an internal tool, a commercial SaaS, or both? | Sponsor + Product | Phase 4 scope, pricing model |
| OQ-2 | Build, buy, or hybrid vs Hivel/minware? | Finance/Procurement | Build scope & budget |
| OQ-3 | Which 2–3 teams are the Phase-1 pilot? | Sponsor | Phase 1 start |
| OQ-4 | Which single CI/CD tool ships first in MVP? | Technical Architect | E1, E3 scope |
| OQ-5 | Pricing model if commercialized (flat-team recommended)? | Product + Finance | Phase 4 |
| OQ-6 | Is VPC/on-prem needed before Phase 3 for any engagement? | Security + Sales | Deployment architecture |
| OQ-7 | What is the org's individual-view opt-in policy? | People + Security | E8 personal views |
| Term | Definition as used in Mallify |
| --- | --- |
| Deployment Frequency | How often the team successfully releases to production, over a chosen window. |
| Lead Time for Changes | Time from first commit to that change running in production. |
| Change Failure Rate | Share of deployments that result in an incident, hotfix, or rollback. |
| MTTR | Mean time to restore service after a failed change or incident. |
| Ticket Lead Time | Time for a whole feature (potentially many PRs) to complete. |
| DORA | The four delivery metrics above; the industry standard for delivery performance. |
| SPACE | Framework covering Satisfaction, Performance, Activity, Communication, Efficiency. |
| Investment Profile | Classification of engineering time as planned, unplanned, or rework. |
| Planned work | Effort aligned to a tracked epic/initiative. |
| Unplanned work | Effort with no epic linkage — interruptions, ad-hoc requests. |
| Rework | Effort revisiting or fixing previously delivered work. |
| AI ROI | Estimated dollar return from AI-assisted work vs non-AI work (speed, quality, rework). |
| RAG status | Red/amber/green indicator of a metric against its configurable target. |
| Time-to-first-insight | Elapsed time from first connector to first populated dashboard (target < 30 min). |
| Connector | An integration that ingests data from one external tool. |
| Semantic / metrics layer | The materialized layer that computes metrics from normalized data. |
| Role | Name | Signature | Date |
| --- | --- | --- | --- |
| Engineering Sponsor |  |  |  |
| Product Owner |  |  |  |
| Technical Architect |  |  |  |
| QA / Security Lead |  |  |  |
| Design Lead (advisory) |  |  |  |
| Next deliverable after sign-off: C4 Architecture Model (context → container → component), owned by the Technical Architect, inheriting the layered architecture and epic structure defined here. The Section 6 epics and stories seed the initial delivery backlog in parallel. |
| --- |