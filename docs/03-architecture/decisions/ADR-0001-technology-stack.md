# ADR-0001: Core technology stack

- **Status:** Accepted
- **Date:** 2026-07-04
- **Deciders:** Vishal, Aditi (per BRD §11.2 suggested direction)
- **BRD traceability:** §11.2 Suggested Technology Direction; NFR Scalability, Performance, Extensibility

## Context

The BRD suggests a stack aligned with existing internal expertise to accelerate delivery and
reduce onboarding time. We need a baseline stack for Phase 1 MVP that can grow through Phase 4.

## Decision

We will build with:

- **Frontend:** React 18 + TypeScript (strict) + Tailwind CSS; Recharts for visualization;
  API client generated from OpenAPI specs.
- **Backend:** Java 21 + Spring Boot 3 (Spring Web, Spring Data JPA, Spring Security).
- **Database:** PostgreSQL 16 (see ADR-0002 for schema strategy).
- **Auth:** OIDC-compatible; JWT access tokens (RS256), Spring Security RBAC.
- **Migrations:** Flyway.
- **Local/dev deployment:** Docker Compose; production containerized (Kubernetes when scale demands).
- **Observability:** OpenTelemetry + Prometheus + Grafana; structured JSON logging.

## Options considered

1. **BRD-suggested stack (chosen)** — matches internal expertise; proven for this shape of
   platform; fastest onboarding.
2. **Node/TypeScript full-stack** — one language across the stack, but discards internal
   Java/Spring expertise the BRD explicitly wants to leverage.
3. **Python (FastAPI) backend** — strong for the future ML/LLM components, but weaker fit for
   the core transactional API vs. existing Spring patterns; Python can still be used for
   isolated Phase 3 ML services if needed (new ADR then).

## Consequences

- Fast start using validated internal patterns; hiring/onboarding aligned with existing teams.
- JVM services carry more memory overhead per connector; acceptable at MVP connector count.
- Phase 3 LLM/agent components may justify a different runtime — decide via new ADR when scoped.
