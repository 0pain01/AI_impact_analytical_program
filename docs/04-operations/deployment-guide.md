# Deployment Guide — What, Why, and How

This is the single document that ties the whole system together: what each service does, why it
exists as a *separate* service instead of being folded into another one, how the pieces talk to
each other, what's containerized today vs. what still runs as a plain process, and — the part
that matters most if you're taking this to a real cloud environment — exactly which values
(secrets, URLs, ports) someone has to supply, and where.

It complements, rather than replaces, the other docs:
- [`docs/03-architecture/system-architecture.md`](../03-architecture/system-architecture.md) —
  the formal C4 diagrams and design drivers.
- [`docs/03-architecture/decisions/`](../03-architecture/decisions/) — the ADRs, i.e. the record
  of *why* a specific technical choice was made, at the moment it was made.
- Each service's own `README.md` — the authoritative, up-to-date reference for that service's
  own config/API surface. When this guide and a service's README disagree, the README is right;
  file an issue against this doc.

---

## 1. What the platform is, in one paragraph

AI Impact Evaluation ingests data from the tools engineering teams already use — GitHub or
GitLab (source control), Jira (ticketing), GitHub Actions or Jenkins (CI/CD), and AI coding
assistants (Claude Code, GitHub Copilot) — and turns it into DORA metrics, PR/code-review
analytics, investment-profile classification (planned vs. unplanned vs. rework), and AI
adoption/ROI figures, all served through role-gated dashboards. Nothing is manually tagged;
every number is derived from events the tools already emit.

## 2. Why it's shaped the way it is (the theory)

Four forces from the BRD shaped every structural decision here, and it's worth understanding
them before reading the service list below, because almost every "why is this its own service"
question traces back to one of them:

1. **Vendor APIs fail routinely, and that must never lose data.** GitHub, GitLab, Jira, and
   Jenkins all have rate limits, outages, and API deprecations on their own schedules, entirely
   outside this platform's control. If a connector talked directly to a database or to another
   service's business logic, a vendor outage or a bad deploy of one connector could cascade into
   data loss elsewhere. The fix (ADR-0002) is architectural: every connector is an isolated
   process whose only job is "fetch from the vendor, publish a raw event to a queue." It cannot
   corrupt shared state because it has no access to any — the queue is the only thing between it
   and the rest of the system.
2. **Every event must be replayable and reproducible.** A metric computed today must be
   re-derivable from the same raw inputs tomorrow, with the same answer — otherwise nobody can
   trust a historical chart. This is why raw events land in an *immutable, append-only* staging
   table (never updated, never deleted) before anything derives typed state or metrics from them
   (ADR-0003). If a metric formula turns out to be wrong, the fix is a new formula version run
   against the same untouched raw log, not a data-repair script.
3. **A new data source should cost "add a service," not "touch everything."** Adding GitLab
   alongside GitHub, or Jenkins alongside GitHub Actions, added zero changes to `metrics-engine`
   in both cases — a new connector publishes events shaped like an existing entity (a merge
   request looks enough like a pull request; a Jenkins build looks enough like a workflow run),
   and the ingestion layer maps both into the *same* shared table. The metrics layer only ever
   reads that shared, provider-agnostic table, so it never needs to know how many source-control
   or CI/CD tools are connected. See §7 for exactly how this plays out for each connector.
4. **No manual tagging, no surveillance.** Every metric must be derivable from data the tools
   already produce as a side effect of normal work — never a status an engineer has to remember
   to update, never a keystroke/idle-time signal. This is a hard product constraint (BRD §5.3),
   not a style preference, and it shows up as a design rule inside individual services (e.g.
   deploy detection matches CI job names/branches instead of asking anyone to flag a "this was a
   deploy" checkbox).

## 3. The shape of the system

```
Sources (GitHub/GitLab, Jira, CI/CD, AI assistants)
    │  webhook or polled backfill
    ▼
Connectors (one per tool)  ──publish──▶  RabbitMQ (aiimpacteval.events exchange)
                                              │  "#" binding — every event, whatever the source
                                              ▼
                                    ingestion-writer (sole writer to staging)
                                              │
                     ┌────────────────────────┼────────────────────────┐
                     ▼                        ▼                        ▼
            staging.raw_event      staging.<entity>_state      identity-service
         (immutable audit log)   (typed, indexed projections)   (contributor/team
                                   ingestion-writer maintains     identity resolution)
                                   incrementally as events land)        │
                                              │                         ▼
                                              ▼                    core.* tables
                                       metrics-engine
                                    (reads staging + core,
                                     writes mart.metric_daily)
                                              │
                                              ▼
                                          api-core
                          (auth, RBAC, dashboard/admin APIs — the only
                           thing the frontend or an external caller talks to)
                                              │
                                              ▼
                                    frontend (React SPA)
```

Everything left of `ingestion-writer` only knows how to talk to RabbitMQ. Everything right of it
only knows how to talk to Postgres and (for `api-core`) to a connector's `/internal/backfill`
endpoint when an admin explicitly triggers one. No connector ever talks to another connector, to
the frontend, or to `api-core` directly (the one exception — `api-core` calling *into* a
connector to trigger a backfill — is described in §7 as "the one arrow that points the other
way," and it's a deliberate, narrow exception, not a hole in the isolation rule).

## 4. Services, in detail

Each entry: what it does, why it's a separate service, what it depends on, and what it exposes.
Ports and env-var defaults below are the **local-dev** defaults; §9 gives the cloud/production
equivalents.

### 4.1 Frontend (`frontend/`)

**What:** the only thing a human ever looks at. React 18 + TypeScript (strict) + Tailwind +
Recharts single-page app. Views: Cockpit (DORA metrics), Teams (org → team/repo drill-down),
Investment Profile, Code Review Analytics, AI Cost Track, Personal Activity (opt-in, IC-only),
Setup/onboarding checklist, and the Admin console (connector health, repo/team management, user
administration, audit log) — each view's visibility is gated by the logged-in user's role.

**Why separate:** it's the presentation layer; nothing here computes a metric or touches a
database directly. `src/api.ts` is a hand-written typed client mirroring `api-core`'s OpenAPI
contract — every number on screen came from an HTTP call to `api-core`, nothing is computed
client-side beyond formatting.

**Depends on:** `api-core` (all data), nothing else directly.

**Runs on:** `http://localhost:5173` in dev (Vite dev server, proxies `/api` to `api-core`);
in production it's a static build (`npm run build`) served by any static host or CDN — it has no
server-side runtime of its own.

### 4.2 api-core

**What:** the only door into the system from the outside. Owns authentication (JWT resource
server, RS256, ADR-0004), RBAC (`ADMIN`/`ENG_LEADER`/`MANAGER`/`IC`/`FINANCE_READONLY`), every
dashboard-facing API (Cockpit metrics, Code Review Analytics, AI Cost Track, Investment Profile,
Jira Work Items, Teams/Repos listing, Setup checklist), the Admin console's APIs (connect a
repo/project/Jira project/Jenkins job, import an org/group, connector health, user role
assignment, audit log), and the Flyway migrations that define every Postgres schema in the
system.

**Why separate:** it's the only service that needs to know about "who is asking and what are
they allowed to see" — centralizing that here means every other service can stay unauthenticated
internally (protected instead by network isolation) rather than reimplementing RBAC five times.

**Depends on:** Postgres (reads `staging`/`core`/`mart`, owns the schema via Flyway). Also
*calls* connector-github/gitlab/jira/jenkins's `/internal/backfill` endpoints — the one
deliberate exception to "connectors never get called into" mentioned in §3: an ADMIN clicking
"Connect a repo" needs *something* to kick off that repo's backfill, and api-core making an
outbound HTTP call is simpler and safer than giving a connector a way to reach back into core
infrastructure.

**Port:** `8080`.

### 4.3 metrics-engine

**What:** computes every metric from staged data — all four DORA metrics (deployment frequency,
lead time for changes, change failure rate, MTTR), PR velocity, and PR cycle time — at repo, org,
and team scope, and writes them into `mart.metric_daily`, the table every dashboard actually
queries. Runs a full recompute on a timer (`METRICS_RECOMPUTE_INTERVAL_MS`, default 5 min) plus
an on-demand `POST /internal/recompute` for smoke tests or right after a backfill.

**Why separate:** metric formulas change independently of the API layer, and a recompute is a
batch-style workload (scan a window, aggregate, upsert) that shouldn't share a JVM/thread pool
with the low-latency dashboard-serving `api-core`.

**Depends on:** Postgres only (reads `staging`, reads `core.team_repo` for team rollups, writes
`mart`). No queue subscription — it works entirely off what's already staged.

**Port:** `8084`.

### 4.4 identity-service

**What:** resolves "GitHub user X, GitLab user Y, Jira account Z, and this git commit's author
email" into one canonical contributor, and imports org/group team structure (GitHub org teams,
GitLab group projects+members) into `core.team`/`core.team_repo`/`core.team_member` — the table
every team-scoped dashboard filter and drill-down depends on.

**Why separate:** identity resolution is genuinely different work from event ingestion or metric
computation — it's a standing subscription to *every* event (`#` binding, its own queue) purely
to extract "who is this," and it deliberately never merges two identities without high-confidence
evidence (an exact email match), rather than guessing.

**Depends on:** RabbitMQ (its own `identity.events` queue, same exchange), Postgres (`core`
schema).

**Port:** `8085`.

### 4.5 ingestion-writer

**What:** the single writer to `staging.raw_event`. Subscribes to *every* event on the shared
exchange (binding `#`) and inserts it idempotently (`ON CONFLICT ... DO NOTHING` on a natural
key), so a queue redelivery or a re-run backfill is always a safe no-op. Also maintains the typed
"latest state" projection tables (`pull_request_state`, `workflow_run_state`,
`pull_request_review_state`, `jira_issue_state`, `ai_usage_state`, `connector_activity`) — these
exist purely so `metrics-engine`/`api-core` read simple indexed columns instead of re-deriving
"latest state per entity" from raw JSON on every query.

**Why separate:** ADR-0003's replay-safety guarantee depends on there being exactly *one* writer
to staging — if every connector wrote to Postgres directly, idempotency would have to be
reimplemented once per connector instead of once, here.

**Depends on:** RabbitMQ (its own `staging.events` queue + DLQ, `#` binding — hears everything),
Postgres (writes `staging`; the schema itself is owned by api-core's Flyway migrations).

**Port:** `8082`.

### 4.6 platform-common

**What:** not a running service — a shared library (`EventEnvelope`, the one shape every
connector wraps its payload in before publishing; `EventTopology`, the RabbitMQ exchange/queue
name constants; `TimeoutRestClients`, a bounded connect/read-timeout `RestClient.Builder` every
connector's outbound vendor client uses so a dropped connection fails fast instead of hanging a
backfill forever). Every other backend module depends on it.

### 4.7 The connectors — one per external tool

Every connector follows the same shape (ADR-0002: "own no business logic"): authenticate to the
vendor, receive webhooks and/or poll for backfill, wrap each entity in an `EventEnvelope`,
publish to `aiimpacteval.events`. None of them read from or write to Postgres directly, and none
of them know about metrics, RBAC, or the frontend.

| Connector | Source-control / tool | Live path | Backfill | Feeds |
|---|---|---|---|---|
| **connector-github** | GitHub (PRs, commits) + GitHub Actions (CI/CD) | Webhook, HMAC-SHA256 verified | PRs, commits, workflow runs, org teams | `pull_request_state`, `workflow_run_state`, `pull_request_review_state`, `core.team*` |
| **connector-gitlab** | GitLab (merge requests, commits, pipelines, groups) | Webhook, shared-secret token | MRs, commits, pipelines, MR **approvals**, group projects+members | Same shared tables as GitHub, `repo` values prefixed `gitlab:` so a same-named project can never collide with a GitHub repo |
| **connector-jira** | Jira issues | Webhook, shared-secret token in URL | Issues with changelog (status-transition history) | `jira_issue_state` |
| **connector-jenkins** | Jenkins builds | Backfill/polling only — no webhook trigger wired up | Full build history per job | `workflow_run_state` (same table GitHub Actions uses; results normalized to a shared lowercase vocabulary) |
| **connector-ai-telemetry** | Claude Code + GitHub Copilot usage | None — file-based backfill only | Reads a usage-report file per tool | `ai_usage_state` |

**Why GitHub and GitLab are both "source control" but separate services, not one connector with a
flag:** their APIs, webhook payload shapes, auth models, and pagination all differ enough that a
single connector branching on "which vendor" internally would be more complex than two small,
independent ones — and a GitLab outage or rate-limit exhaustion then genuinely cannot affect
GitHub ingestion, which a shared process could risk.

**Why GitHub Actions and Jenkins both write into `workflow_run_state` instead of two separate
CI/CD tables:** DORA's deployment-frequency/CFR/MTTR queries only care about "was there a
successful build/deploy, when, on what repo" — a schema already shaped that generically the
moment GitHub Actions was built meant Jenkins support (added later) required zero
`metrics-engine` changes, only a values-normalization step in `ingestion-writer` (Jenkins reports
`SUCCESS`/`FAILURE` uppercase; the DORA queries hardcode lowercase `success` — silently missing
that mapping would have made every Jenkins deploy invisible to DORA with no error, which is
exactly the kind of bug this architecture is built to make loud, not quiet).

**GitHub ↔ GitLab feature parity is a hard product requirement in this codebase**, not just good
practice: the platform's real target customers run private GitLab, and GitHub was only ever used
to build and demo the product. Every capability built for GitHub — DORA metrics, PR velocity,
Code Review Analytics' cycle-stage breakdown and reviewer-load — has a matching, independently
verified GitLab implementation. The one place they genuinely differ is GitLab's approval model
itself: GitLab only reports "approved, by whom, when," with no equivalent to GitHub's "changes
requested"/"commented" review states — a real platform difference, documented rather than
papered over with a guessed mapping.

## 5. Infrastructure

### 5.1 PostgreSQL — three schemas, one instance (for now)

| Schema | Written by | Read by | Purpose |
|---|---|---|---|
| `staging` | `ingestion-writer` only | `metrics-engine`, `api-core` | `raw_event` (immutable audit/replay log) + typed "latest state" projections |
| `core` | `identity-service`, `api-core` | `metrics-engine`, `api-core` | Contributors, teams, team-repo mappings, app users/RBAC, audit log |
| `mart` | `metrics-engine` only | `api-core` | `metric_daily` — the pre-aggregated table every dashboard query actually hits |

One Postgres instance with three schemas is a deliberate MVP simplification (ADR-0002) — the
schema boundary is drawn so that splitting `mart` out to a dedicated analytical store later (or
moving to a managed multi-instance setup) is a connection-string change, not a re-architecture.

### 5.2 RabbitMQ — one exchange, everything fans out from it

- Exchange: `aiimpacteval.events` (topic exchange). Every connector publishes here.
- `ingestion-writer`'s queue (`staging.events`, binding `#`) hears every single event — it's the
  system's one true "log everything" consumer.
- `identity-service`'s queue (`identity.events`, binding `#`) also hears everything, filtering in
  code — adding this second full-firehose consumer touched zero connector code (ADR-0003's whole
  point: a new consumer is just a new queue bound to the existing exchange).
- Dead-letter exchange (`aiimpacteval.events.dlx`) + `staging.events.dlq`: a message
  `ingestion-writer` can't process (malformed JSON, etc.) is rejected without requeue, landing in
  the DLQ rather than being silently dropped or retried forever.

### 5.3 Docker images — what's containerized today, and how much is left

| Service | Containerized? | Notes |
|---|---|---|
| **connector-gitlab** | **Yes** (ADR-0005) | Multi-stage build: `maven:3.9-eclipse-temurin-21-alpine` compiles, `eclipse-temurin:21-jre-alpine` ships (no build toolchain in the runtime image), runs as a non-root user created in the image. Build context is the Maven **reactor root** (`services/`), because the image needs the parent POM and `platform-common` — see `services/connectors/connector-gitlab/Dockerfile`. |
| **connector-jenkins** | **Yes** (ADR-0007) | Same template as connector-gitlab, exactly — see `services/connectors/connector-jenkins/Dockerfile`. |
| **Jenkins CI server itself** | **Yes**, as infra (ADR-0007) | Not application code — `jenkins/jenkins:lts` run as a plain `infra/docker-compose.yml` service (`jenkins`), same treatment as Postgres/RabbitMQ. Needed only for local dev/testing against a real Jenkins instance; a real deployment points `JENKINS_BASE_URL` at whatever Jenkins your org already runs, and doesn't need this service at all. |
| Everything else (frontend, api-core, metrics-engine, identity-service, ingestion-writer, connector-github/jira/ai-telemetry) | No — plain process | Run via `mvn spring-boot:run` / `java -jar` (backend) or `npm run dev` / a static build (frontend), started by `infra/start-backend.sh` for local dev. |

**Why not everything is containerized yet:** connector-gitlab was the newest addition at the
point Docker packaging was introduced, and containerizing it first established the template
(base images, build strategy, non-root user) deliberately, rather than containerizing everything
at once and discovering the pattern was wrong across nine services simultaneously. ADR-0005
explicitly scoped itself to just that one service; ADR-0007 later extended the same template to
connector-jenkins (plus brought the local Jenkins CI server under the same compose stack) for the
identical reason — friction actually observed (the Admin console's Jenkins "Refresh" needing a
manually-started connector every time), not a blanket policy change. **Six backend services are
still plain processes. If you're taking this to a real cloud deployment, containerizing the rest
is where you start** — see §6.

## 6. Containerizing the rest, for a real deployment

None of the other six services need any code change to run in a container — they're already
plain Spring Boot (or, for the frontend, static-buildable) apps with all configuration already
externalized to environment variables (see §4 and §8). Copy `connector-gitlab`'s Dockerfile
pattern for each: a `maven:3.9-eclipse-temurin-21-alpine` build stage (with the same reactor-root
build-context trick — only the sibling modules' *POMs* get copied in for dependency resolution,
never their sources, so editing one service can't invalidate another's Docker layer cache),
`eclipse-temurin:21-jre-alpine` runtime stage, non-root user. The frontend needs a different
shape entirely — it's static output (`npm run build` → `dist/`), so its "image" (if you want one
rather than a plain CDN/static host) is just an `nginx:alpine` (or similar) serving that
directory, not a JVM runtime at all.

## 7. Cloud deployment — step by step

### 7.1 Prerequisites — what you need before you start

**Infrastructure:**
- A managed PostgreSQL 16 instance (or self-hosted) reachable from every backend service.
- A managed RabbitMQ instance (or self-hosted) reachable from every connector +
  `ingestion-writer` + `identity-service`.
- A place to run 9 backend containers (or processes) + serve the static frontend build — any
  container orchestrator (ECS, Cloud Run, Kubernetes, etc.) or, for a smaller pilot, a handful of
  VMs behind a reverse proxy.
- TLS termination in front of everything reachable from the public internet (the frontend, and
  any connector receiving live webhooks).

**Per-integration credentials** — only needed for the tools you're actually connecting; every
connector runs and starts up fine with none of these set, it simply has nothing to ingest until
they're supplied:

| Integration | What to generate | Where |
|---|---|---|
| GitHub | A fine-grained personal access token, scoped to **Public repositories** (read-only) if every connected repo is public, or to the specific private repos otherwise — least privilege (BRD rule 4) | github.com → Settings → Developer settings → Personal access tokens |
| GitLab | A project or group access token, **Reporter** role, `read_api` scope only | GitLab project/group → Settings → Access Tokens |
| Jira | An Atlassian API token + the account email it belongs to | id.atlassian.com → Security → API tokens |
| Jenkins | A Jenkins API token for a user with read access to the jobs you want ingested | Jenkins → user profile → Configure → API Token |
| AI telemetry | Real usage-report exports from Anthropic's Admin API / GitHub's Copilot Metrics API (the connector currently reads a file shaped exactly like each API's real response — see connector-ai-telemetry's README for the exact swap point) | Anthropic Console / GitHub org settings |

**If you're pointing at a Jenkins instance you're standing up yourself** (rather than an
existing org Jenkins), note that the Jenkins server itself needs its own one-time setup wizard
completed (unlock key, admin user, initial plugins) before an API token can even be generated for
it — this is unrelated to `connector-jenkins`, which is just an HTTP client against whatever
Jenkins server already exists.

**Webhook reachability:** any connector meant to receive *live* webhooks (GitHub, GitLab, Jira)
needs a publicly reachable HTTPS URL once deployed — this is automatic once the connector is
actually deployed behind your ingress/load balancer; it's only a local-dev problem (needing a
tunnel like ngrok), not a cloud-deployment one.

**Local-dev gotcha, not a cloud-deployment one:** `infra/docker-compose.yml` reads `infra/.env`
automatically for the two containerized connectors (gitlab, jenkins). A connector started
directly via `mvn spring-boot:run` (github, jira, ai-telemetry — or gitlab/jenkins run outside
Docker) does **not** get `infra/.env` for free; source it into your shell first
(`set -a; source infra/.env; set +a` on bash) or it runs unauthenticated/misconfigured with no
error at startup — the symptom shows up later as a real backfill call failing (e.g. connector-jira
throwing `UnknownHostException: unconfigured.invalid`), not as a clear "missing credential" error
up front.

### 7.2 Deployment order

1. **Postgres** — provision it, note the connection string. `api-core` owns the Flyway
   migrations; the first successful `api-core` startup against a fresh database creates every
   schema and table. Nothing else should start writing before that first successful `api-core`
   boot.
2. **RabbitMQ** — provision it, note the connection details. No schema/topology setup needed —
   every service declares its own exchange/queue/binding on startup (`RabbitConfig` classes),
   idempotently.
3. **api-core** — first service to actually start against the real database (runs the
   migrations). Confirm `GET /actuator/health` returns healthy before continuing.
4. **ingestion-writer** and **identity-service** — the two "hear everything" consumers. Start
   these before any connector, so the first events a connector publishes aren't lost waiting for
   a consumer to exist (RabbitMQ will queue them regardless once the queue itself is declared,
   but there's no reason to race it).
5. **metrics-engine** — starts computing on its own schedule; fine to start any time after
   `api-core`'s migrations have run.
6. **The connectors you need** (github/gitlab/jira/jenkins/ai-telemetry) — each independent of
   the others; start only the ones you have credentials for.
7. **frontend** — the static build, served last since it's purely a client of `api-core`.

### 7.3 Consolidated environment variable / secret reference

Every value below is a plain environment variable today (no secrets-manager integration is
built in) — in a real deployment, inject these from your platform's secret store (AWS Secrets
Manager, GCP Secret Manager, Kubernetes Secrets, etc.) rather than plain env vars in a manifest,
and never commit them to source control (this repo's own local convention: `infra/.env`,
gitignored).

| Service | Var | Required for | Notes |
|---|---|---|---|
| *(all backend)* | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Everything | Same Postgres instance, different services need different schema access in principle (least-privilege: consider a role per schema) |
| *(all backend except metrics-engine, api-core)* | `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | Everything that publishes or consumes events | Same broker |
| api-core | `AUTH_DEV_TOKEN_ENABLED=false` | **Production — mandatory** | The dev-token bridge (`POST /auth/dev-token`) must be disabled once real OIDC/SSO is wired up; leaving it on in production is an authentication bypass |
| api-core | `CONNECTOR_GITHUB_BASE_URL`, `CONNECTOR_GITLAB_BASE_URL`, `CONNECTOR_JIRA_BASE_URL`, `CONNECTOR_JENKINS_BASE_URL` | Admin console connect flows + scheduled auto-refresh | Internal service-to-service URLs (private network / service mesh, never public) |
| connector-github | `GITHUB_WEBHOOK_SECRET`, `GITHUB_TOKEN` | Live webhooks / backfill respectively | Webhook secret must match what's configured on the GitHub webhook itself |
| connector-gitlab | `GITLAB_WEBHOOK_SECRET`, `GITLAB_TOKEN`, `GITLAB_API_BASE_URL` | Live webhooks / backfill / self-managed GitLab | Same secret-matching requirement as GitHub |
| connector-jira | `JIRA_WEBHOOK_SECRET`, `JIRA_BASE_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN` | Live webhooks / backfill | `JIRA_BASE_URL` is your Atlassian Cloud site, e.g. `https://yourorg.atlassian.net` |
| connector-jenkins | `JENKINS_BASE_URL`, `JENKINS_USERNAME`, `JENKINS_API_TOKEN` | Backfill (no webhook path exists yet) | |
| connector-ai-telemetry | `CLAUDE_CODE_USAGE_FILE`, `COPILOT_USAGE_FILE` | Backfill | File paths — in a real deployment, this becomes an authenticated API call instead (see §4.7's connector table); until then, the file needs to be mounted/present wherever this connector runs |
| ingestion-writer, api-core | `COPILOT_MONTHLY_SEAT_COST_USD` | AI Cost Track accuracy | Both services compute against this independently — must be set to the same value in both, or AI Cost Track's numbers become internally inconsistent |
| api-core | `AI_LICENSED_SEATS`, `AI_BLENDED_HOURLY_RATE_USD` | AI Cost Track's adoption-rate denominator and ROI dollar conversion | Real, org-specific numbers — never hardcode a guess |
| metrics-engine | `METRICS_DEPLOY_WORKFLOW_PATTERN`, `METRICS_HOTFIX_WORKFLOW_PATTERN` | Deploy/hotfix detection | **Must include your real deploy branch name** (e.g. `main|production`) for GitLab pipelines specifically, since GitLab pipelines carry no per-run name to match against the way a GitHub Actions workflow or Jenkins job does |

Every service also has a `SERVER_PORT` (see §4 for the local-dev default of each) — in a
container orchestrator this typically doesn't need to change, since each service gets its own
network identity regardless of which port it listens on internally.

### 7.4 Production hardening not yet built

Documented here rather than glossed over, per this repo's own no-fabrication standard:

- **Auth:** the dev-token bridge is a pilot/local-dev stand-in (ADR-0004) — a real deployment
  needs OIDC/SSO wired into `api-core`'s Spring Security config before `AUTH_DEV_TOKEN_ENABLED`
  is turned off.
- **Secrets:** nothing here integrates with a secrets manager yet; env vars are it.
- **Multi-tenancy:** the schema reserves a tenant-ID column for Phase 4 but nothing enforces
  tenant isolation today — this is a single-tenant deployment as it stands.
- **Rate-limit resilience:** connectors detect and surface an exhausted vendor rate limit (429,
  not a generic 500) but don't yet adaptively throttle ahead of hitting it.
- **Webhook-gap healing:** no poller reconciles "did we miss a webhook while a connector was
  down" — the periodic backfill/auto-refresh (§4.7, ADR-0006) partially covers this for
  Jira/Jenkins, but GitHub/GitLab rely on their webhook delivery being reliable.

## 8. Feature history and the reasoning behind it

This is a condensed narrative of *why* things were added, in the order they were added — the
full blow-by-blow lives in [`docs/CHANGELOG.md`](../CHANGELOG.md) and the individual
[ADRs](../03-architecture/decisions/); this section is the "connect the dots" version.

- **The core pipeline came first** (connectors → queue → staging → metrics → API → frontend),
  built around GitHub + Jira + GitHub Actions, because that's the smallest set of tools that
  exercises every layer of the architecture at once (source control, ticketing, CI/CD).
- **RBAC and audit logging (ADR-0004)** came before any dashboard went further than a
  proof-of-concept, because retrofitting access control onto dashboards that already exist is
  exactly the kind of security debt the engineering standards forbid accumulating.
- **Jenkins was added as a second CI/CD source** specifically to prove the "provider-agnostic
  shared table" design actually holds up under a second, structurally different vendor (GitHub
  Actions and Jenkins don't share a data model at all) — it required a values-normalization step
  in `ingestion-writer` and nothing else, validating the whole premise of §2's third design
  driver.
- **GitLab was added** once it became clear the platform's actual target customers run private
  GitLab, not GitHub — GitHub had only ever been the vehicle for building and demoing the
  product. This is the single biggest reason the GitHub/GitLab feature-parity rule (§4.7) exists
  as an explicit, enforced requirement rather than a nice-to-have: a gap invisible in a
  GitHub-only demo (Code Review Analytics silently having zero data for GitLab, for instance)
  directly undermines the actual sales pitch.
- **`ConnectorAutoRefreshService` / ADR-0006** exists because Jira and Jenkins have no live
  webhook path in this deployment — without a scheduled re-check, their Admin-console health
  status would go stale the moment nobody happened to click "Refresh," which is exactly the kind
  of manual babysitting the BRD's "no manual tagging" principle argues against, applied to
  connector health rather than metric tagging.
- **GitLab merge-request approvals** were the last piece of the GitHub/GitLab parity story —
  added once it was confirmed Code Review Analytics' cycle-stage breakdown and reviewer-load
  silently showed nothing for any GitLab repo, verified end-to-end against a real approval and
  merge on a live GitLab project rather than assumed correct from the API docs.
- **The per-repo Cockpit drill-down** (Teams tab → Repositories) closed the last visibility gap:
  DORA metrics already worked correctly for GitLab, but the only way to *see* a single repo's own
  numbers was to first assign it to a team. Given GitLab is the platform's real target-customer
  surface, "technically correct but two clicks of indirection away" wasn't good enough.
- **Jira Work Items dashboard** — a Jira-specific detail view (backlog composition, resolution
  metrics, an open-issue worklist) distinct from Investment Profile's cross-tool
  Planned/Unplanned/Rework lens on the same underlying Jira data; `staging.jira_issue_state`
  widened (V14) with standard Jira fields only (priority, status category, reporter, labels, due
  date — never an instance-specific custom-field guess).
- **connector-jenkins containerized, and the local Jenkins CI server brought under the compose
  stack** (ADR-0007) — extends connector-gitlab's containerization template (ADR-0005) to the
  second connector, specifically to remove the friction of the Admin console's Jenkins "Refresh"
  needing a manually-started connector every time.
- **Admin console gained Jira/Jenkins connector parity** — connect a Jira project or Jenkins job,
  see live sync status, Refresh, Delete — mirroring the GitHub/GitLab repo controls exactly
  (minus team assignment, since neither has a project/job-to-team mapping). Verifying this
  surfaced a real, pre-existing, cross-connector limitation: deleting an item and immediately
  reconnecting it doesn't reliably restore its data when nothing changed upstream, because every
  connector's backfill republishes events under a deterministic idempotency key that
  `staging.raw_event`'s own uniqueness constraint silently deduplicates before the projection
  is rebuilt. Documented, not silently patched — the real fix touches ADR-0003's idempotency
  contract for every connector, not just Jira/Jenkins, and needs its own decision first.
