# ADR-0007: Containerize connector-jenkins and bring the local Jenkins CI server under the compose stack

- **Status:** Accepted
- **Date:** 2026-09-17
- **Deciders:** Engineering
- **BRD traceability:** NFR Extensibility (§11.2); FR-1.3 (CI/CD ingestion); ADR-0002 (queue-isolated connectors); ADR-0005 (containerize connector-gitlab — the template this follows)

## Context

ADR-0005 containerized `connector-gitlab` as the first departure from "every backend service
runs as a plain local process," explicitly leaving the door open for a second containerized
service to follow the same template rather than requiring its own from-scratch design. Two
things converged to make `connector-jenkins` that second service:

1. Local dev/testing here already runs a real Jenkins CI server as a **standalone** Docker
   container (`jenkins/jenkins:lts`, a plain `docker run`, not part of any compose project) —
   the connector's own implementation was verified against it (see its backfill service javadoc).
   Sitting outside `infra/docker-compose.yml`'s `ai-impact-evaluation` project, it doesn't come
   up with the rest of the stack and has to be started/tracked separately.
2. `connector-jenkins` itself still only ran as a local `mvn spring-boot:run` process, same as
   `connector-github`/`connector-jira`/`connector-ai-telemetry` — so even with the Jenkins server
   running, getting build data flowing required a third manual step.

Net effect: exercising the Jenkins path (Admin console "Refresh" on a Jenkins-backed connection,
or just wanting build data in the dashboards) meant juggling two things outside `docker compose
up` — the CI server itself and the connector polling it — on top of everything else. Bringing
both under the same stack as `connector-gitlab` removes that friction and matches what ADR-0005
anticipated ("the template the next containerized service will copy... or deliberately deviate
from" — this doesn't deviate).

## Decision

Two additions to `infra/docker-compose.yml`, both under the `ai-impact-evaluation` project:

1. **`jenkins`** — the real Jenkins CI server, `jenkins/jenkins:lts`, unchanged from how it
   already ran standalone (same image, same `9090:8080`/`50000:50000` port mapping). It reuses
   the **pre-existing** `jenkins_home` named Docker volume (declared `external: true`) rather
   than a fresh compose-managed one, so the real job history/configuration already on this
   machine (the `aie-pipeline` job referenced throughout `connector-jenkins`'s own docs) is
   preserved, not recreated empty. This is infrastructure we consume, not application code we
   build — no Dockerfile of our own, same treatment `postgres`/`rabbitmq` already get.
2. **`connector-jenkins`** — packaged with the exact same multi-stage Dockerfile shape ADR-0005
   established for `connector-gitlab` (Maven/Alpine build stage → JRE/Alpine non-root runtime
   stage), wired to the compose-internal `jenkins` service (`JENKINS_BASE_URL=http://jenkins:8080`,
   not the host-facing `localhost:9090` its README still documents for the no-Docker path) and
   the shared `rabbitmq` service, same as `gitlab`.

`connector-gitlab` remains unchanged; this doesn't touch it beyond a doc-comment update noting
it's no longer the *only* containerized connector.

## Options considered

1. **Chosen: containerize `connector-jenkins` (ADR-0005's template) + bring the existing
   standalone Jenkins server into the same compose project, reusing its volume** — one `docker
   compose up` now exercises the full Jenkins path; no new pattern introduced, the volume reuse
   means zero data loss for the real job history already on this machine.
2. **Leave Jenkins server standalone, only containerize `connector-jenkins`** — half the
   friction removed, but the CI server still has to be started/tracked separately from
   everything else; doesn't fully address the problem this ADR exists to solve.
3. **Recreate a fresh Jenkins volume/container instead of reusing `jenkins_home`** — simpler
   compose file (no `external: true` volume to reason about), but destroys the real
   `aie-pipeline` job history and configuration that `connector-jenkins`'s own docs reference as
   verified-against — rejected outright, this is exactly the kind of state-destroying shortcut
   to avoid.
4. **Containerize every remaining backend service now** (github, jira, ai-telemetry, api-core,
   etc.) — ADR-0005 already named this as a larger, separately-decided future push; still out of
   scope here, this ADR only extends the pattern to the two Jenkins-related pieces actually in
   friction.

## Consequences

- `docker compose up` (or `up -d gitlab jenkins connector-jenkins`) now brings up both
  containerized connectors and the Jenkins CI server itself alongside Postgres/RabbitMQ — the
  Admin console's "Refresh" against a Jenkins-backed connection works without any manual
  `mvn spring-boot:run` step, matching how `connector-gitlab` already behaves.
- The `jenkins_home` volume predates this ADR and isn't declared `external: true` by accident —
  anyone reusing this compose file on a machine **without** that volume already present needs to
  either create it first (`docker volume create jenkins_home`) or drop `external: true` to let
  compose manage a fresh one; this is called out in the volume's own comment in
  `docker-compose.yml` so it isn't a silent trap.
- `connector-jenkins`'s host-facing config (`JENKINS_BASE_URL=http://localhost:9090` default in
  its own `application.yml`/README) is intentionally left as-is for the "run locally without
  Docker" path — the compose service overrides it internally, same split `connector-gitlab`
  already has between its README's two run modes.
- Same follow-up this ADR inherits from ADR-0005: a future full containerization push should
  reconsider whether `start-backend.sh`/`stop-backend.sh` still make sense as a shrinking
  mixed local/containerized setup.

## Notes

`services/connectors/connector-jenkins/Dockerfile`, `infra/docker-compose.yml` (`jenkins` and
`connector-jenkins` services), `infra/.env.example`.
