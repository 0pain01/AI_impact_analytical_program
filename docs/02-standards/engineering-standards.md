# Engineering Standards — AI Impact Evaluation

These are the corporate standards, rules, and best practices for building AI Impact Evaluation. They are
binding for all contributors — human and AI agents. Deviations require an ADR.

## 1. Guiding principles

1. **Trust is the product.** AI Impact Evaluation measures other engineers' work; a wrong DORA number or a
   metric that feels like surveillance kills adoption. Correctness and transparency of metric
   computation outrank feature velocity.
2. **Automate, never ask engineers to change behavior.** No manual tagging, no required commit
   conventions from end users, no status-update discipline (BRD BO-2, NFR Usability).
3. **Fail soft at the edges, never lose data.** Vendor APIs will break; the queue absorbs it.
4. **Contract-first, docs-with-code.** OpenAPI specs and documentation change in the same PR
   as the code.

## 2. Version control & branching

- **Trunk-based development**: `main` is always releasable. Short-lived feature branches
  (`feat/<ticket>-<slug>`, `fix/…`, `chore/…`), merged via PR within ~2 days of creation.
- **Conventional Commits** required: `feat:`, `fix:`, `docs:`, `refactor:`, `perf:`, `test:`,
  `chore:`, `ci:`. Scope encouraged: `feat(connector-github): …`. Breaking changes: `!` + a
  `BREAKING CHANGE:` footer.
- **No direct pushes to `main`.** Every change goes through a PR with at least one approval
  (human or designated AI review + human sign-off).
- PR descriptions must reference the requirement or ticket (e.g., `FR-1.4`, `MAL-123`) and
  state what documentation was updated.
- Keep PRs small — target < 400 changed lines. Oversized PRs get split. (We build a tool that
  flags oversized PRs; we don't merge them ourselves.)
- **Semantic versioning** for all released artifacts and APIs.

## 3. Code style & quality

### Backend (Java 21, Spring Boot)
- Google Java Style, enforced via Spotless + Checkstyle in CI.
- Constructor injection only; no field injection. No `@Autowired` on fields.
- Immutable DTOs (records) at API boundaries; never expose JPA entities over HTTP.
- Explicit transaction boundaries; no long-running transactions around external API calls.
- Static analysis: Error Prone + SonarQube quality gate (no new blocker/critical issues).

### Frontend (React 18+, TypeScript strict)
- `strict: true`; no `any` without an inline justification comment.
- ESLint + Prettier enforced in CI; functional components + hooks only.
- Data fetching through a typed API client generated from the OpenAPI spec — no hand-written
  fetch types.
- Component states for loading/error/empty are mandatory for every dashboard widget.
- Accessibility: WCAG 2.1 AA; charts must have accessible data-table fallbacks.

### General
- No dead code, no commented-out code in `main`.
- Feature flags for incomplete features rather than long-lived branches.
- All timestamps stored and transmitted in UTC (ISO-8601); timezone conversion at the UI layer
  only. Metric window boundaries computed in the org's configured timezone — document this in
  every metric definition.

## 4. Testing standards

- **Test pyramid:** many unit tests, targeted integration tests, few E2E smoke tests.
- **Metric computations are the crown jewels:** every metric (DORA, lead-time stages,
  investment classification) requires table-driven unit tests covering edge cases —
  empty ranges, timezone boundaries, rebased/force-pushed history, reverted deployments,
  unlinked tickets. A metric without tests does not ship.
- **Connectors** are tested against recorded fixtures (WireMock/VCR-style) of real vendor API
  payloads, including error/rate-limit responses. Fixtures are checked in and versioned.
- Coverage gate: 80% line coverage on `services/metrics-engine` and identity normalization;
  70% elsewhere. Coverage is a floor, not a target.
- CI must be green to merge; flaky tests are quarantined within 24h and fixed within a sprint.
- Every bug fix includes a regression test reproducing the bug.

## 5. API standards

- **Contract-first:** OpenAPI 3.1 spec updated before implementation; spec lives with the
  service and is validated in CI against the implementation.
- REST, JSON, `application/json`; resource-oriented URLs; plural nouns (`/api/v1/teams/{id}/metrics`).
- **Versioned from day one:** `/api/v1/…`. Breaking changes require a new version + migration note.
- Errors follow RFC 9457 (Problem Details): `type`, `title`, `status`, `detail`, `traceId`.
- Pagination: cursor-based for event-scale data; `limit` capped server-side.
- All list endpoints support the BRD's standard filters where applicable: team, repository,
  sprint, date range (FR-8.2.5).
- Rate limiting and request tracing (correlation IDs) on every service.

## 6. Data & database standards

- **Migrations only** (Flyway); no manual schema changes in any environment. Migrations are
  forward-only and backward-compatible for one release (expand → migrate → contract).
- Raw ingested events are **immutable** — append-only staging tables; corrections happen in
  derived layers, never by mutating raw data (auditability + reprocessability).
- Every ingested record carries: source system, source ID, ingestion timestamp, connector
  version — enabling replay and debugging.
- Idempotent ingestion: natural keys / upserts so replays and webhook retries never duplicate.
- PII minimization: store only contributor identifiers needed for identity resolution
  (name, email, tool user IDs). No message contents, no code contents beyond diffs' metadata
  needed for metrics. See security standards for retention.

## 7. Resilience & operations

- Connectors communicate with the core via **message queue**; a vendor outage or a connector
  crash must never take down the API or lose events (BRD FR-1.8, NFR Availability).
- Retries with exponential backoff + jitter; dead-letter queues with alerting; poison messages
  quarantined, never dropped silently.
- Respect vendor rate limits (adaptive throttling); back off on `429`/`Retry-After`.
- **Observability from day one:** structured JSON logs with correlation IDs, RED metrics
  (rate/errors/duration) per service, connector-health dashboard, alerting on ingestion lag
  (> 15 min lag breaches the NFR).
- Health endpoints (`/health/live`, `/health/ready`) on every service.
- 12-factor configuration: env vars, no environment-specific builds.

## 8. Documentation standards (see also CLAUDE.md — enforced)

- ADRs for all architectural decisions (`docs/03-architecture/decisions/`), MADR-style template.
- Per-service README: purpose, local run, tests, config, API surface.
- Metric definitions in `docs/01-product/metric-definitions.md`: formula, sources, edge cases,
  BRD traceability. Published in-app too — metric transparency is an adoption requirement.
- Runbooks for every alertable condition in `docs/04-operations/`.
- `docs/CHANGELOG.md` entry for every user-visible or architectural change.
- Diagrams as code (Mermaid) so they diff and stay current.

## 9. Definition of Done

A change is done when: code reviewed and merged → tests written and green → OpenAPI/spec
current → docs updated (architecture/ADR/README/metric definitions/CHANGELOG as applicable) →
observability in place for new paths → security checklist passed (see security standards) →
deployed behind a flag if incomplete.
