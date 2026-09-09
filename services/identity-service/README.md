# identity-service

Identity & normalization layer (C4 container "Identity Service", PRD E2, FR-1.4/FR-1.5):
consumes the event stream and reconciles per-tool identities (GitHub user, GitLab user, Jira
account, git commit signature) into canonical contributors in `core.contributor` /
`core.contributor_alias`.

**Status:** E2-S1 core — alias short-circuit, exact-email merge (confidence 0.90), new-contributor
creation, bot detection, git-signature fallback (`email:<addr>` handles). E2-S2 team import —
consumes `github.team.snapshot` (from connector-github's team backfill) and
`gitlab.group.snapshot` (from connector-gitlab's group backfill — GitLab's closest analogue to
a GitHub org's teams), upserts `core.team`/`core.team_repo`/`core.team_member` tagged by
source (`github`/`gitlab`/`manual`), resolving each member through the same identity resolver.
GitLab identity extraction (merge-request author, git-signature commits/pushes) never observes
an email for platform-user identities — GitLab's API doesn't expose one outside the
authenticated user's own profile — so those members only cross-link to a GitHub/Jira identity
once also observed with an email elsewhere. Not yet: name-similarity heuristics, Admin
review/merge/split API + UI, sub-team hierarchy (`core.team.parent_team_id` exists but nothing
populates it yet).

## Behavior

- Own queue `identity.events` (binding `#`, filters in code) with DLQ — adding this consumer
  touched no connector (ADR-0003).
- Resolution: known alias → existing contributor; else exact normalized-email match → attach
  alias with confidence 0.90 (Admin-reviewable); else new contributor (confidence 1.00).
  Never merges without an email match — unresolved stays separate rather than guessed.
- Bots (`*[bot]`, dependabot/renovate/github-actions/copilot) flagged `is_bot` and excluded
  from people-metrics by default (BRD privacy posture).
- Team import: `team.snapshot`/`group.snapshot` events upsert by `(source, sourceId)` — safe to
  re-run. `core.team_repo` mappings feed metrics-engine's team-level rollups (E4-S2
  drill-down); GitLab-sourced repo values are prefixed `gitlab:`, matching the prefix
  `ingestion-writer`'s `StagingEventWriter` stamps on GitLab's own state rows, so the join
  actually lines up.

## Configuration (env vars)

| Var | Default |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local Postgres defaults (writes `core` schema) |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | localhost defaults |
| `SERVER_PORT` | `8085` |

## Tests

`mvn test -pl identity-service` — pure-logic suites: resolver (dedup, email merge with
confidence, no-silent-merge, bot flagging), event extraction (PR/MR webhook + snapshot parity,
commit author precedence, email-handle fallback, Jira assignee/reporter/actor, GitLab MR
author/webhook-actor fallback + git-signature commits/pushes, odd-payload tolerance), team/group
snapshot parsing (GitHub and GitLab), and team import (repo/member mapping, source tagging,
re-import upserts rather than duplicating).
