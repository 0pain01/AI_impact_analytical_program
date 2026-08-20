# connector-jenkins

Ingestion-layer connector for Jenkins (PRD E1-S3, alt. CI/CD source alongside `connector-github`'s
GitHub Actions handling). Publishes raw Jenkins build data to the `aiimpacteval.events` exchange
per ADR-0003. Owns **no business logic** (ADR-0002); writes into the same provider-agnostic
`staging.workflow_run_state` projection GitHub Actions already feeds — no separate CI/CD schema.

**Status:** backfill/polling only, not yet webhook-driven (no equivalent of Jenkins' Generic
Webhook Trigger plugin wired up). No incremental "since" cursor — each backfill call re-fetches
the job's full build list (bounded by Jenkins' own build retention).

## Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /internal/backfill?jobName={job}` | Fetches the named job's build history and publishes one `build.snapshot` event per build. Not exposed publicly — invoked by api-core when a Jenkins job is connected, or directly for onboarding/debugging. |
| `GET /actuator/health` | Liveness/readiness. |

## Event types published

`jenkins.build.snapshot` — one per build, idempotency key `jenkins:{jobName}:{buildNumber}`
(build numbers only reset per-job in Jenkins, not globally).

## How ingestion-writer projects it

`StagingEventWriter.upsertJenkinsBuildState` writes into `staging.workflow_run_state` alongside
GitHub Actions rows. Two things are Jenkins-specific there, both load-bearing:
- **Result normalization:** Jenkins reports `SUCCESS`/`FAILURE`/`UNSTABLE`/`ABORTED`;
  metrics-engine's DORA queries hardcode lowercase `conclusion = 'success'`. Stored verbatim,
  every Jenkins build would silently never match those queries. Mapped to
  `success`/`failure`/`failure`/`cancelled`.
- **Repo attribution:** the git remote isn't on the build object directly — it's inside whichever
  entry of the (mostly-empty-object) `actions` array has
  `"_class": "hudson.plugins.git.util.BuildData"`. A build with no Git plugin data (e.g. a
  freestyle job with no SCM configured) is published with `repo = "unknown"` rather than dropped.

## Configuration (env vars)

| Var | Default | Purpose |
|---|---|---|
| `JENKINS_BASE_URL` | *(empty)* | e.g. `http://localhost:9090` |
| `JENKINS_USERNAME` / `JENKINS_API_TOKEN` | *(empty)* | Basic auth (Jenkins user profile → Configure → API Token) |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | localhost defaults | Queue connection |
| `SERVER_PORT` | `8086` | HTTP port |

## Tests

None yet for this connector or for `StagingEventWriter`'s Jenkins-handling path — a known gap,
not an oversight. `StagingEventWriterIntegrationTest` (in `ingestion-writer`) should be extended
with a SUCCESS build, a FAILURE build, and a build with no `BuildData` action (confirms the
`repo = "unknown"` fallback).
