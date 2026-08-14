# BRD Summary — AI Impact Evaluation Engineering Intelligence & Analytics Platform

> Condensed from: *AI Impact Evaluation — Engineering Intelligence Platform BRD v1.0* (04-Jul-2026, Vishal & Aditi).
> This summary is the working reference; the signed BRD is authoritative for disputes.

## Vision

A single, trustworthy, real-time view of software delivery performance and AI-tooling ROI.
AI Impact Evaluation unifies Git, Jira, CI/CD, code quality, incident, and AI-assistant data into one
analytics layer with role-based dashboards. Strategic position: the **automated, no-tagging
DORA/SPACE analytics of minware, delivered through the leadership-friendly UX of Hivel** —
while explicitly avoiding surveillance features.

## Business objectives

| ID | Objective |
|---|---|
| BO-1 | Single source of truth for engineering delivery performance |
| BO-2 | Automate DORA & SPACE-aligned metrics — no manual status updates |
| BO-3 | Quantify AI coding assistant adoption & ROI in financial terms |
| BO-4 | Reduce code review cycle time and defect leakage |
| BO-5 | Visibility into planned vs. unplanned vs. rework time |
| BO-6 | Role-based dashboards for Exec / Manager / IC personas |
| BO-7 | (Optional) Commercialize as multi-tenant SaaS |

## Success metrics for the platform itself

- Time-to-first-insight after tool connection: **< 30 minutes**
- Manual reporting effort for EMs: **−70%** within 2 quarters
- Adoption: **80%+** of target teams active within 90 days
- Data trust score (survey): **≥ 4/5** among engineering leads
- A demonstrable **AI ROI dollar figure** for ≥1 assistant in first release of that module

## Scope by phase

| Phase | Duration | Deliverables |
|---|---|---|
| 0 — Discovery | 2–3 wks | BRD approval, PRD, C4 model, pilot team selection |
| 1 — MVP | 8–10 wks | GitHub/GitLab + Jira + one CI/CD integration; DORA dashboard; Cockpit executive view; RBAC |
| 2 — Depth | 6–8 wks | Investment Profile, PR/code-review analytics, Goals/OKR, SonarQube integration |
| 3 — AI & Advanced | 8–12 wks | AI adoption & ROI module, AI code-review agent, custom report/query layer, on-prem/VPC option |
| 4 — Scale (optional) | Ongoing | Multi-tenant SaaS packaging, SOC 2 Type II, external clients |

**Out of scope (permanent or deferred):** individual surveillance features (permanent, ethical
exclusion); replacing Jira/GitHub (permanent — analytics layer only); HR performance-review
document automation (deferred beyond Phase 3).

## Functional requirement modules (BRD §8)

| Module | Highlights | Priority |
|---|---|---|
| 8.1 Data Integration | OAuth connectors (Git FR-1.1, Jira FR-1.2, CI/CD FR-1.3); identity de-duplication (FR-1.4); team-structure import (FR-1.5); SonarQube (FR-1.6); AI telemetry (FR-1.7); resilient pipeline, no data loss (FR-1.8) | Must (1.5–1.7 Should) |
| 8.2 DORA & Delivery | All 4 DORA metrics auto-computed; lead-time stage breakdown (commit→PR→review→merge→deploy); ticket-level lead time; CFR via incident/hotfix/rollback linkage; filter by team/repo/sprint/date | Must |
| 8.3 Cockpit | Single-pane exec dashboard; RAG thresholds; org→team→IC drill-down with access control; exportable reports | Must |
| 8.4 Investment Profile | Jira×Git classification: planned / unplanned / rework; scope-creep trends; cost capitalization support | Phase 2 |
| 8.5 Code Review Analytics | PR size distribution, review turnaround, reviewer load; stale/oversized PR flags; (P3) AI first-pass review agent | Phase 2/3 |
| 8.6 AI Adoption & ROI | AI-assisted vs. not: cycle time, defects, rework; dollar ROI; adoption pattern detection | Phase 3 |
| 8.7 Goals & OKR | Targets tied to live metrics, auto-tracked | Phase 2 |
| 8.8 Admin & RBAC | Roles: Admin, Engineering Leader, Manager, IC, Finance/Read-only; visibility rules; full audit log | Must |
| 8.9 Custom Reporting | Pre-built report library + guided custom-metric builder (no mandatory query language) | Phase 3, Should |

## Non-functional requirements (BRD §9)

| Category | Requirement |
|---|---|
| Performance | Dashboards < 3 s at ~1M events; metric refresh within 15 min of source event |
| Scalability | Horizontally scalable ingestion; 10,000+ contributors over platform lifetime |
| Security | SOC 2 Type II readiness (P2/3); encryption in transit & at rest; least-privilege API scopes |
| Data privacy | No surveillance metrics by design; individual views opt-in, growth-framed |
| Availability | 99.5% SaaS uptime; ingestion degrades gracefully (queue & retry) during vendor outages |
| Usability | < 30 min time-to-first-dashboard-value; zero workflow change required from engineers |
| Auditability | Config changes, access grants, exports logged & retrievable ≥ 12 months |
| Deployment | P1 SaaS multi-tenant; P3 VPC/on-prem ingest agent |
| Extensibility | New source connectors without core re-architecture |

## Data sources (BRD §10)

Source control (GitHub/GitLab/Bitbucket) · Project mgmt (Jira/Azure Boards/Linear) ·
CI/CD (Jenkins/GitHub Actions/Azure Pipelines/GitLab CI) · Code quality (SonarQube) ·
Incidents (PagerDuty/Opsgenie) · AI assistants (Copilot/Cursor/Claude Code) ·
Optional: Slack/Calendar for SPACE wellbeing signals.

## Personas

CTO/VP Eng (Cockpit, AI ROI) · Engineering Manager (team dashboard, Dev360, Investment Profile) ·
Tech Lead (code review analytics) · IC (opt-in personal view) · Product/Program Manager
(Investment Profile, cycle time) · Finance (AI ROI financial report) · Security/Compliance
(admin & audit console).

## Key risks to design against

1. **Surveillance perception** → no individual tracking, opt-in views, transparent metric definitions.
2. **Data inaccuracy** from inconsistent tool usage → identity normalization + heuristics + pilot validation.
3. **Scope creep** toward full Hivel/minware clone → strict phase gates.
4. **Vendor API breakage** → queue/retry ingestion, connector health monitoring.
5. **Slow time-to-value** → < 30 min setup, no query language for core use.
6. **Build-vs-buy** — final decision at end of Phase 0; internal build proceeds via this repo.
