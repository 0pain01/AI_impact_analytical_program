# ADR-0008: De-containerize connector-gitlab and connector-jenkins

- **Status:** Accepted
- **Date:** 2026-09-17
- **Deciders:** Engineering
- **BRD traceability:** NFR Extensibility (§11.2); supersedes ADR-0005 (containerize
  connector-gitlab) and partially supersedes ADR-0007 (containerize connector-jenkins — only the
  connector's own containerization, not the real Jenkins CI server also introduced there)

## Context

ADR-0005 containerized `connector-gitlab` — not because GitLab ingestion needed it, but because
it was the newest connector at the point Docker packaging was introduced into this repo, and
containerizing it first established a template deliberately rather than converting every service
at once. ADR-0007 then extended that same template to `connector-jenkins`, motivated by a real,
specific friction: the Admin console's "Refresh" on a Jenkins-backed connection did nothing
useful if nobody had remembered to separately `mvn spring-boot:run` the connector first.

Revisiting both with that distinction in mind:

- **connector-gitlab** talks to the real `gitlab.com` API (or a self-managed instance via
  `GITLAB_API_BASE_URL`) directly over the network — exactly like `connector-github` talks to
  `api.github.com`. It has no same-stack dependency a container can health-gate startup on, and
  its own README already documented a fully-equivalent "run locally without Docker" path from day
  one. Its containerization never solved a problem specific to GitLab; it solved "establish a
  Docker template," which has been established and extended (to `connector-jenkins`) since.
- **connector-jenkins** does have a same-stack dependency (the local Jenkins CI server), but the
  actual friction ADR-0007 named — "forgetting to start the connector" — is a `start-backend.sh`
  problem, not something that inherently requires a container to fix. Adding it to
  `start-backend.sh`'s list of auto-started plain processes (which nothing before this ADR
  actually prevented) removes the same friction without needing Docker at all.

Running a mixed local/containerized stack has a real, ongoing cost that both ADR-0005 and
ADR-0007 flagged as a future concern and neither resolved: two different start/stop mechanisms
(`start-backend.sh`/`stop-backend.sh` vs. `docker compose`), two different places to look for
logs, and a concrete bug already hit earlier in this project's history — `connector-jenkins`
briefly double-started (once via Docker, once as a stray plain process) and port-conflicted on
`:8086`, entirely because of this exact split. With only two of nine backend services ever having
been containerized, and neither for a reason that survives scrutiny once compared side by side,
the mixed setup was pure cost with no longer any matching benefit.

## Decision

We will **remove `connector-gitlab` and `connector-jenkins` from `infra/docker-compose.yml`** and
run both as plain processes via `infra/start-backend.sh`, same as `connector-github`,
`connector-jira`, and `connector-ai-telemetry` already do.

- `infra/start-backend.sh` now starts nine plain-process backend services (was seven), adding
  `connector-gitlab` (port 8088, no extra config needed — its defaults already work
  unconfigured) and `connector-jenkins` (port 8086, with `JENKINS_BASE_URL` exported just for
  that process, pointed at `http://localhost:${JENKINS_PORT:-9090}` — the host-facing address of
  the Jenkins server below).
- The real Jenkins CI server (`jenkins/jenkins:lts`) **stays** in `infra/docker-compose.yml`,
  unchanged — it is infrastructure we consume, not application code we containerized by choice
  (same category as Postgres/RabbitMQ), and there's no equivalent "just point at the real SaaS"
  option for Jenkins the way GitHub/GitLab have. Nothing about this ADR touches it.
- Both connectors' Dockerfiles are **kept in the repo**, unbuilt by compose — either connector can
  still be built and run in a container manually (see each README's "Run with Docker" section) if
  a future need arises; this ADR removes them from the default local-dev path, not from the repo.

## Options considered

1. **Chosen: de-containerize both connectors, keep the real Jenkins CI server containerized** —
   removes the mixed-stack cost entirely for application code, changes nothing about how any
   connector's actual ingestion logic works (env vars, endpoints, health checks all identical),
   and both connectors already had a documented, working "run locally" path before this ADR.
2. **De-containerize connector-gitlab only, leave connector-jenkins containerized** — addresses
   the weaker of the two original justifications while keeping ADR-0007's stated friction fix, but
   leaves the mixed-stack cost (two start mechanisms) unresolved for the sake of one connector;
   rejected once `start-backend.sh` auto-starting it was confirmed to remove the same friction.
3. **Containerize every remaining service instead** (the "go the other direction" option ADR-0005
   and ADR-0007 both flagged as a future possibility) — consistent, and the right call if/when a
   real deployment needs container images for everything, but out of scope for a local-dev-only
   decision and a much larger review surface than reverting two services.
4. **Leave both as-is** — no code change, but keeps paying the mixed-stack cost (two start/stop
   mechanisms, two places to look for logs, the double-start class of bug already seen once) for
   a template-establishment reason that no longer applies now the template exists in two Docker-
   files that aren't going anywhere.

## Consequences

- `infra/docker-compose.yml` now only runs infrastructure again (Postgres, RabbitMQ, the real
  Jenkins CI server) — no application code, closer to its original shape before ADR-0005.
- `infra/start-backend.sh`/`stop-backend.sh` are the single way to start/stop every backend
  service; `docker compose up`/`down` only ever needs to be touched for infra. This is the
  simplification both ADR-0005 and ADR-0007 named as an open question — resolved here in favor of
  "one mechanism," rather than "containerize everything."
- No functional change to either connector: same env vars, same `/actuator/health` endpoint, same
  event types published, same ports. Verified locally after this change — `connector-gitlab` and
  `connector-jenkins` both start cleanly via `start-backend.sh` and pass their health checks with
  the `gitlab`/`connector-jenkins` compose services removed.
- A real cloud deployment still needs container images for every service eventually (per
  `deployment-guide.md` §6) — this ADR doesn't change that end state, only what's convenient for
  local dev today; whoever does that containerization pass later should treat all nine backend
  services uniformly rather than reusing "these two happen to already have a Dockerfile" as a
  reason to special-case them.

## Notes

`infra/docker-compose.yml`, `infra/start-backend.sh`, `infra/stop-backend.sh`,
`services/connectors/connector-gitlab/README.md`,
`services/connectors/connector-jenkins/README.md`. Supersedes ADR-0005 entirely; supersedes only
the `connector-jenkins` containerization half of ADR-0007 (its `jenkins` CI-server-as-infra
decision stands unchanged).
