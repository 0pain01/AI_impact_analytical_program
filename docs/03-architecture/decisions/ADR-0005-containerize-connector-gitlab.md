# ADR-0005: Containerize connector-gitlab with a multi-stage Docker build

- **Status:** Superseded by ADR-0008 (connector-gitlab de-containerized — see that ADR for why)
- **Date:** 2026-09-09
- **Deciders:** Engineering
- **BRD traceability:** NFR Extensibility (§11.2); FR-1.1 (source-control ingestion); ADR-0002 (queue-isolated connectors)

## Context

ADR-0002 already establishes "one connector service per external tool" as the extensibility
pattern, and the architecture diagram anticipated GitLab as a source-control connector
alongside GitHub. Adding `connector-gitlab` is therefore not itself a new architectural
decision — but *how it runs* is: every existing backend service (including all four existing
connectors) runs as a plain `mvn spring-boot:run` / `java -jar` process via
`infra/start-backend.sh`, and `infra/docker-compose.yml` has so far only ever run
infrastructure (Postgres, RabbitMQ), never application code. Packaging `connector-gitlab` as a
Docker image is the first departure from that, so it needs its own record rather than being
silently bolted onto ADR-0002 — the base image, build strategy, and user model chosen here are
the template the next containerized service will copy or deliberately deviate from.

## Decision

`connector-gitlab` ships with a **multi-stage Dockerfile**:

1. **Build stage** — `maven:3.9-eclipse-temurin-21-alpine`. Build context is the Maven reactor
   root (`services/`) so the image can resolve the parent POM and build+install
   `platform-common` before packaging the connector; only the POMs of sibling modules are
   copied in (needed for reactor graph resolution), never their sources, so editing another
   service can't invalidate this connector's Docker layer cache.
2. **Runtime stage** — `eclipse-temurin:21-jre-alpine` (JRE only, no build toolchain in the
   shipped image), running as a **non-root user** created in the image (`aiimpacteval`), per
   the security standards' least-privilege posture applied to the container itself, not just
   the app's own OAuth/API scopes.
3. Everything else about the connector is unchanged: same env-var configuration surface as the
   locally-run connectors, same `/actuator/health` endpoint used for both `docker-compose`
   healthchecks and `start-backend.sh`-style polling.

Only `connector-gitlab` is containerized for now. The other four connectors and the remaining
services keep running as local processes; this ADR does not mandate containerizing them.

## Options considered

1. **Chosen: multi-stage Docker build, Alpine JDK/JRE base images, non-root runtime user** —
   small final image, no build tooling shipped, matches the JRE 21 target already fixed by
   ADR-0001; Alpine's `apk`-based images are the common lightweight default for JVM containers.
2. **Single-stage build (JDK image straight through)** — simpler Dockerfile, but ships the
   entire Maven/JDK toolchain in the runtime image — larger attack surface and image size for
   no operational benefit.
3. **`mvnw` wrapper committed to the repo instead of a Maven-preloaded base image** — more
   portable (pins the exact Maven version), but the repo doesn't have a wrapper today and
   nothing else in the build depends on one; using `maven:3.9-eclipse-temurin-21-alpine`
   avoids introducing a second Maven-invocation mechanism for a single connector.
4. **Containerize every backend service now** — consistent, but out of scope for this change
   and a larger review surface than one connector; deferred to a future ADR if/when the team
   decides to containerize the rest.

## Consequences

- `infra/docker-compose.yml` now builds application code, not just infrastructure — anyone
  running `docker compose up` for the first time will trigger a Maven build inside the
  container on first `gitlab` start (cached afterward via the `~/.m2` build-cache mount).
  `start-backend.sh` is intentionally untouched: it still lists 8 locally-run services, and
  `connector-gitlab` is started separately via `docker compose ... up gitlab` per its README.
- Establishes the template (base images, build-context choice, non-root user) for
  containerizing any other service later — follow it or supersede this ADR, don't diverge
  silently.
- A future push to containerize all services should also reconsider whether `infra/`'s
  `start-backend.sh`/`stop-backend.sh` scripts still make sense as a mixed local/containerized
  setup, or whether docker-compose should become the single way to run the stack.

## Notes

`services/connectors/connector-gitlab/Dockerfile`, `services/connectors/connector-gitlab/README.md`.
