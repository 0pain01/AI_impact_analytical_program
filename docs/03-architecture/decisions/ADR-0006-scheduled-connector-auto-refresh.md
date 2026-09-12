# ADR-0006: Scheduled auto-refresh for connectors with no live webhook path

- **Status:** Accepted
- **Date:** 2026-09-12
- **Deciders:** Engineering
- **BRD traceability:** BO-2/FR-1 (automatic metric derivation — "never require engineers to
  change how they work" extended here to "never require an admin to babysit connector health");
  PRD E1-S4/E8 (Admin console connector health); ADR-0002 (queue-isolated connectors); ADR-0003
  (event envelope / idempotency); V11 migration (`staging.connector_activity`)

## Context

`AdminConnectorService` (V11) already reports two independent signals per connector: `last data
change` (from `staging.raw_event`, advances only when something genuinely new lands) and `last
checked` (from `staging.connector_activity`, advances on every event processed, duplicates
included) — specifically so a connector that's working fine but has nothing new to report
doesn't look dead. `deriveStatus` marks a connector `STALE` once `last checked` is more than 24h
old.

That design assumed something keeps `last checked` moving even when nothing changes. For
GitHub/GitLab that's a live webhook once one is configured on the real repo/project. Jira and
Jenkins have no webhook wired up in this deployment (connector-jenkins was built backfill-only —
see its class javadoc; connector-jira's webhook exists but isn't pointed at anything here
either) — the only thing that ever calls their `/internal/backfill` endpoint is an ADMIN manually
clicking "Refresh" (or, before an Admin-console Jira/Jenkins connect flow existed at all, a
one-off terminal `curl`). Once that stops happening, `last checked` sits frozen and both
connectors correctly, but unhelpfully, show `STALE` — indistinguishable in the UI from an actually
broken connection, and requiring a human to notice and click a button just to prove otherwise.
This is the exact class of manual-babysitting the BRD's "no manual tagging dependency" principle
argues against, applied to connector health rather than metric tagging.

## Decision

We will add `ConnectorAutoRefreshService` (api-core, `@Scheduled(fixedDelayString =
"${connectors.auto-refresh-interval-ms:1800000}")`) that, every 30 minutes by default, re-triggers
backfill for every Jira project key and Jenkins job name this platform has ever ingested data for:

- Jira project keys: `SELECT DISTINCT project_key FROM staging.jira_issue_state`.
- Jenkins job names: recovered from `staging.raw_event.source_id`, whose shape is
  `jenkins:{jobName}:{buildNumber}` (connector-jenkins's own ADR-0003 idempotency key) —
  `split_part(source_id, ':', 2)` needs no new column or table.

This calls the *same* `/internal/backfill` endpoints a manual "Refresh" click already hits — no
new connector-side code. `JiraBackfillService`/`JenkinsBackfillService` already republish a
snapshot event for every issue/build in scope on every call, not only deltas, so
`StagingEventWriter`'s unconditional `connector_activity` upsert advances `last_checked_at` even
when a cycle finds zero real changes. No new heartbeat event type or schema change was needed —
the existing V11 signal already answers the right question once something calls it regularly.

Deliberately **not** routed through `ConnectorAdminService`'s `triggers` map (that's a per-repo
Sync-status UX signal for GitHub/GitLab repos; Jira/Jenkins don't participate in that table) or
`AuditLog` (the BRD's audit rule covers configuration changes, access grants, and data exports — a
routine background health-check is none of those; a WARN log per failed item is enough for an
operator to notice a genuinely broken connection).

GitHub and GitLab are deliberately **out of scope** for this scheduler: both already have a
"Refresh"/"Refresh all" button and a real webhook path for production use, and blindly
re-backfilling every known repo (some hundreds of PRs/reviews/workflow runs deep) every 30 minutes
would burn meaningful API-rate-limit quota for no benefit — see the GitHub token exhaustion
incident from a 13-repo manual "Refresh all" burst that motivated checking this quota concern at
all (docs/CHANGELOG.md, 2026-09-12).

## Options considered

1. **Chosen: scheduled re-backfill of known Jira projects/Jenkins jobs, reusing existing connector
   endpoints** — no connector-side changes, no schema changes, reuses the already-correct V11
   staleness signal exactly as designed.
2. **A dedicated lightweight "heartbeat" endpoint per connector** (e.g. `POST
   /internal/heartbeat` that only touches `connector_activity`, no real vendor API call) — cheaper
   per cycle, but would make "Connected" mean "the connector process is up," not "we successfully
   reached Jira/Jenkins and re-verified the data" — a materially weaker and arguably dishonest
   signal for a status the Admin console presents as connector *health*, not process liveness.
3. **A persistent "connected sources" table**, populated by a real Admin-console connect flow for
   Jira/Jenkins (mirroring GitHub org import / GitLab group import) — more architecturally
   complete, but a substantially larger change (new endpoints, OpenAPI, frontend forms) than the
   staleness problem itself calls for; deferred. `staging.jira_issue_state`/`raw_event` already
   answer "what have we ever connected" without it.
4. **Extend the same scheduler to GitHub/GitLab repos too** — rejected for now; see Decision above
   (unnecessary rate-limit cost, existing webhook path already covers production use).

## Consequences

- Jira and Jenkins now self-heal their Admin console status without any manual action, as long as
  `connectors.jira.base-url`/`connectors.jenkins.base-url` (new config keys, mirroring
  `connectors.github`/`connectors.gitlab`) and each connector's own vendor credentials
  (`JIRA_EMAIL`/`JIRA_API_TOKEN`, `JENKINS_USERNAME`/`JENKINS_API_TOKEN`) are actually configured
  — if they aren't, the scheduler honestly logs a WARN and the connector correctly stays
  `STALE`/`NOT_CONNECTED` rather than being made to falsely look healthy (no-fabrication
  principle).
- `api-core` now needs `@EnableScheduling` (added to `ApiCoreApplication`) — the first scheduled
  job in this service (`metrics-engine` already has one).
- A Jira project or Jenkins job that stops existing upstream (deleted/renamed) will keep being
  polled harmlessly every cycle (a 404/error, logged and skipped) until someone manually cleans up
  its rows in `staging.jira_issue_state`/`raw_event` — no automatic "forget this" path exists yet.
  Acceptable for now; revisit if this becomes a real operational nuisance.
- Follow-up not done here: no automated test for `ConnectorAutoRefreshService` itself (it's a thin
  orchestration wrapper — the interesting logic it calls is already tested in each connector) —
  flagged, not silently skipped.

## Notes

`services/api-core/src/main/java/com/aiimpacteval/apicore/admin/ConnectorAutoRefreshService.java`,
`services/api-core/src/main/java/com/aiimpacteval/apicore/admin/AdminConnectorService.java` (the
`STALE_AFTER`/two-signal design this builds on), `services/connectors/connector-jenkins/README.md`
("no webhook support" gap this addresses for Jenkins specifically).
