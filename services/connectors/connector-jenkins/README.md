# connector-jenkins

Ingestion-layer connector for Jenkins (PRD E1-S3, alt. CI/CD source alongside `connector-github`'s
GitHub Actions handling). Publishes raw Jenkins build data to the `aiimpacteval.events` exchange
per ADR-0003. Owns **no business logic** (ADR-0002); writes into the same provider-agnostic
`staging.workflow_run_state` projection GitHub Actions already feeds — no separate CI/CD schema.

**Status:** backfill/polling only, not yet webhook-driven (no equivalent of Jenkins' Generic
Webhook Trigger plugin wired up). No incremental "since" cursor — each backfill call re-fetches
the job's full build list (bounded by Jenkins' own build retention).

Runs as a plain process via `infra/start-backend.sh`, same as every other connector.
`infra/docker-compose.yml` still runs a real Jenkins CI server (`jenkins/jenkins:lts`) as local
dev/test infrastructure — this connector talks to it at `http://localhost:9090`. It *was*
packaged as a Docker image itself (ADR-0007, following `connector-gitlab`'s ADR-0005 template),
specifically to avoid the Admin console's "Refresh" silently doing nothing when nobody had
manually started this connector — but was de-containerized (ADR-0008) along with
`connector-gitlab`; `start-backend.sh` now starts it automatically like every other plain-process
connector, so that original friction is addressed a different way (by not being forgettable in
the first place) rather than by containerizing it. The Dockerfile is kept for reference — see
"Run with Docker" below — but is no longer built by `infra/docker-compose.yml`.

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

## Run locally (without Docker)

```
mvn -pl connectors/connector-jenkins -am spring-boot:run
```

Requires RabbitMQ reachable per the env vars above (`infra/docker-compose.yml` starts it), and
`JENKINS_BASE_URL` pointing at a real Jenkins instance — `http://localhost:9090` if using the
Jenkins server `infra/docker-compose.yml` also runs (see below).

## Run with Docker (optional — kept for reference, not used by `infra/docker-compose.yml`)

Build context is the Maven reactor root (`services/`), since the image needs the parent POM
and `platform-common`:

```
docker build -f connectors/connector-jenkins/Dockerfile -t connector-jenkins .
docker run --rm -p 8086:8086 \
  -e RABBITMQ_HOST=host.docker.internal \
  -e JENKINS_BASE_URL=http://host.docker.internal:9090 \
  connector-jenkins
```

This connector is no longer wired into `infra/docker-compose.yml` (ADR-0008) — the Dockerfile
still builds and runs standalone as shown above if you want it containerized for some other
reason, but the default local-dev path is "Run locally" above (which `start-backend.sh` does for
you, `JENKINS_BASE_URL` included). `infra/docker-compose.yml` still runs the real Jenkins CI
server this connector needs, whichever way you run the connector itself.

## Tests

None yet for this connector or for `StagingEventWriter`'s Jenkins-handling path — a known gap,
not an oversight. `StagingEventWriterIntegrationTest` (in `ingestion-writer`) should be extended
with a SUCCESS build, a FAILURE build, and a build with no `BuildData` action (confirms the
`repo = "unknown"` fallback).
