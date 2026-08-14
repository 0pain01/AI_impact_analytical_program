# identity-service

Identity & normalization layer (C4 container "Identity Service", PRD E2, FR-1.4/FR-1.5):
consumes the event stream and reconciles per-tool identities (GitHub user, Jira account, git
commit signature) into canonical contributors in `core.contributor` / `core.contributor_alias`.

**Status:** E2-S1 core — alias short-circuit, exact-email merge (confidence 0.90), new-contributor
creation, bot detection, git-signature fallback (`email:<addr>` handles). E2-S2 team import —
consumes `github.team.snapshot` (from connector-github's team backfill), upserts
`core.team`/`core.team_repo`/`core.team_member`, resolving each member through the same
identity resolver. Not yet: name-similarity heuristics, Admin review/merge/split API + UI,
sub-team hierarchy (`core.team.parent_team_id` exists but nothing populates it yet).

## Behavior

- Own queue `identity.events` (binding `#`, filters in code) with DLQ — adding this consumer
  touched no connector (ADR-0003).
- Resolution: known alias → existing contributor; else exact normalized-email match → attach
  alias with confidence 0.90 (Admin-reviewable); else new contributor (confidence 1.00).
  Never merges without an email match — unresolved stays separate rather than guessed.
- Bots (`*[bot]`, dependabot/renovate/github-actions/copilot) flagged `is_bot` and excluded
  from people-metrics by default (BRD privacy posture).
- Team import: `team.snapshot` events upsert by `(source, sourceId)` — safe to re-run.
  `core.team_repo` mappings feed metrics-engine's team-level rollups (E4-S2 drill-down).

## Configuration (env vars)

| Var | Default |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local Postgres defaults (writes `core` schema) |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | localhost defaults |
| `SERVER_PORT` | `8085` |

## Tests

`mvn test -pl identity-service` — pure-logic suites: resolver (dedup, email merge with
confidence, no-silent-merge, bot flagging), event extraction (PR webhook + snapshot parity,
commit author precedence, email-handle fallback, Jira assignee/reporter/actor, odd-payload
tolerance), team snapshot parsing, and team import (repo/member mapping, re-import upserts
rather than duplicating).
