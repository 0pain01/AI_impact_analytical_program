# api-core

Application/API layer (C4 container "API Core"): authentication, RBAC, dashboard/report APIs,
goals/OKR (Phase 2), admin console APIs, audit logging.

**Status:** owns Flyway migrations (V1 schemas + core tables, V2 `mart.metric_daily`,
V3 audit `actor_email`, V4 `core.team_repo` + mart `scope_id`/`scope_type` rename), serves the
Cockpit dashboard, teams-list, and Admin console (connector health) endpoints, and **enforces
RBAC** — an RS256 JWT resource server with the five BRD roles (ADR-0004, E8). A gated dev-token
bridge issues tokens until OIDC lands; append-only audit log with admin-only read.

## Run locally

```bash
# 1. Start Postgres + RabbitMQ (see infra/README.md)
cd ../../infra && docker compose up -d

# 2. Run the service (needs JDK 21+)
cd ../services && mvn spring-boot:run -pl api-core
```

Health: `GET http://localhost:8080/actuator/health`

## Configuration (env vars — never commit secrets)

| Var | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/aiimpacteval` | Postgres JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `aiimpacteval` / `aiimpacteval_local` | DB credentials (local defaults only) |
| `SERVER_PORT` | `8080` | HTTP port |
| `CONNECTOR_GITHUB_BASE_URL` | `http://localhost:8081` | Where `ConnectorAdminService` calls connector-github's internal backfill endpoints when an ADMIN connects a repo/org from the Admin console |

## Tests

`mvn test -pl api-core`. Standard: business logic requires unit tests; repository/migration
changes require Testcontainers-based integration tests (bring the Testcontainers setup with
the first real feature).

## API surface

Contract-first: [`src/main/resources/openapi/api-core.yml`](src/main/resources/openapi/api-core.yml)
— update the spec before implementing endpoint changes (engineering standards §5).

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /api/v1/auth/dev-token?email=&role=` | public (gated, dev-only) | Issues a short-lived role-scoped JWT (ADR-0004). Disable with `AUTH_DEV_TOKEN_ENABLED=false` in production. |
| `GET /api/v1/metrics/cockpit?days=30&scope=*` | analytical roles | Cockpit tiles from the mart (E4-S1/E4-S2): daily series + aggregate, freshness `asOf`, inline definitions. `scope` is a repo full name, `*` for org, or a team id from `GET /teams` |
| `GET /api/v1/teams` | analytical roles | Lists teams (id, name, repoCount) for the org → team drill-down picker (E4-S2) |
| `GET /api/v1/setup/status` | ADMIN, ENG_LEADER | Onboarding checklist (git/ticketing/CI/dashboard readiness) and time-to-value figure, derived from staging/mart timestamps (E1-S5) |
| `GET /api/v1/audit?limit=100` | ADMIN | Recent audit entries, newest first (E8-S3) |
| `GET /api/v1/admin/connectors` | ADMIN | Per-connector health (status/last sync/event count) derived from `staging.raw_event` timestamps (E1-S4/E8) |
| `GET /actuator/health` | public | Liveness/readiness |

Get a token then call a protected endpoint:

```bash
TOKEN=$(curl -s -X POST "localhost:8080/api/v1/auth/dev-token?email=you@org&role=ENG_LEADER" | jq -r .token)
curl localhost:8080/api/v1/metrics/cockpit -H "Authorization: Bearer $TOKEN"
```
