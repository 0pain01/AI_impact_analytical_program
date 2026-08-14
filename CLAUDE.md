# AI Impact Evaluation — Engineering Intelligence & Analytics Platform

AI Impact Evaluation is an AI-native Software Engineering Intelligence (SEI) platform. It ingests data from
source control (GitHub/GitLab), project management (Jira), CI/CD (GitHub Actions/Jenkins), code
quality (SonarQube), incident management (PagerDuty), and AI coding assistants (Copilot, Cursor,
Claude Code), and computes DORA metrics, investment/time-allocation analytics, PR-review
analytics, and AI ROI — served through role-based dashboards for Executives, Managers, and ICs.

Source of truth for requirements: `docs/01-product/brd-summary.md` (condensed from the signed BRD)
and `docs/01-product/prd.md` (PRD v1.0 — epics E1–E11 with acceptance criteria; mirrors the signed
docx and tracks delivery status per epic; reference story IDs like `E1-S4` in PRs).
Source of truth for architecture: `docs/03-architecture/system-architecture.md`.

## Non-negotiable product rules (from the BRD — never violate these)

1. **No surveillance features.** Never implement keystroke tracking, idle-time tracking, screen
   monitoring, or any individual-level surveillance metric. Individual activity views are opt-in
   and framed around growth, not monitoring. This is an explicit ethical exclusion (BRD §5.3).
2. **No manual tagging dependency.** All metrics (DORA, lead time, CFR, MTTR) must be derived
   automatically from tool data — never require engineers to change how they work.
3. **Analytics layer only.** AI Impact Evaluation never writes back to or replaces Jira/GitHub/CI tools
   (exception: the Phase 3 AI review agent posting PR comments).
4. **Least-privilege integrations.** Request the minimum OAuth/API scopes needed per connector.
5. **Auditability.** Every configuration change, access grant, and data export must be written
   to the audit log (12+ month retention).

## Documentation policy — MANDATORY for all AI agents and humans

Documentation is part of the definition of done. **No code change is complete until its
documentation is updated in the same commit/PR.** Specifically:

- **Before writing code**, read `docs/03-architecture/system-architecture.md` and
  `docs/02-standards/engineering-standards.md`. Code that contradicts them is rejected.
- **Architectural decisions require an ADR.** Any choice of framework, datastore, queue,
  protocol, third-party service, or cross-cutting pattern must be recorded in
  `docs/03-architecture/decisions/` using `ADR-0000-template.md`, numbered sequentially,
  before or with the implementing change. Never silently deviate from an accepted ADR —
  supersede it with a new one.
- **Architecture doc stays current.** If a change adds/removes a service, datastore, queue,
  external integration, or alters a data flow, update the diagrams and component tables in
  `docs/03-architecture/system-architecture.md` in the same PR.
- **Every service/module ships with a README** covering: purpose, how to run locally, how to
  test, configuration/env vars, and its API surface (link to OpenAPI spec).
- **APIs are contract-first.** Update the OpenAPI spec before implementing endpoint changes.
- **New metrics require a metric definition** in `docs/01-product/metric-definitions.md`
  (create it with the first metric): name, formula, data sources, edge cases, and the BRD
  requirement it satisfies. Metric transparency is a trust requirement, not nice-to-have.
- **Update `docs/CHANGELOG.md`** with a one-line entry for every user-visible or
  architecturally significant change.
- **Runbooks:** anything that can page a human (ingestion failures, connector outages, queue
  backlogs) gets a runbook in `docs/04-operations/`.

When you finish any task in this repo, verify this checklist before declaring done:
architecture doc current → ADR written if a decision was made → service README current →
OpenAPI current → metric definitions current → CHANGELOG entry added.

## Engineering standards (summary — full version in docs/02-standards/)

- **Stack:** React + TypeScript + Tailwind (frontend); Java 21 + Spring Boot (backend);
  PostgreSQL (transactional); message-queue-isolated connector services (see ADR-0001/0002).
- **Git:** trunk-based with short-lived feature branches; Conventional Commits
  (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`); PRs required — no direct pushes
  to `main`; PRs small (< ~400 changed lines where feasible).
- **Testing:** unit tests required for all business logic (metric computations especially —
  DORA math bugs destroy user trust); integration tests for connectors against recorded API
  fixtures; no merge with failing or skipped tests.
- **Security:** no secrets in code or config files — env vars/secret manager only; encrypt in
  transit (TLS) and at rest; validate/sanitize all external API payloads; OWASP Top 10 review
  for every endpoint; SOC 2 Type II readiness is a standing constraint.
- **Errors & resilience:** connectors must tolerate vendor API failures (retry with backoff,
  dead-letter queues, idempotent ingestion) — data loss on upstream outage is a defect.
- **Naming/IDs:** requirement IDs from the BRD (FR-x.x, BO-x) referenced in PR descriptions
  so changes trace back to requirements.

## Repository layout (target)

```
/frontend            React + TS dashboard app
/services
  /api-core          Spring Boot application/API layer (auth, RBAC, dashboards API)
  /metrics-engine    Metric computation (DORA, lead time, investment profile)
  /identity-service  Contributor identity & team-structure normalization
  /connectors/*      One service per external tool (github, jira, ci, sonarqube, ai-telemetry)
/docs                All documentation (see docs/README.md for the index)
/infra               IaC, docker-compose for local dev, CI pipeline definitions
```
