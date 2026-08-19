-- Investment Profile (PRD E5-S1) needs a per-issue projection to classify git activity against —
-- connector-jira has been publishing issue.snapshot / jira:issue_* events into staging.raw_event
-- all along, but nothing ever read them back out. This is the pull_request_state/
-- workflow_run_state pattern (V5) applied to Jira: a typed, indexed "latest known state" table
-- instead of re-deriving it from JSONB on every query.
--
-- Deliberately no inline backfill UPDATE here (unlike V5/V7) — the reopened-detection logic
-- needs to scan nested changelog history arrays, which is exactly the kind of thing that's much
-- safer to write once in tested Java (StagingEventWriter.wasReopened) than as one-shot
-- hand-rolled JSONB SQL with no way to unit test it before it runs against real data. Populating
-- existing issues is a re-run of connector-jira's own POST /internal/backfill?projectKey=
-- instead — it re-fetches from Jira's API with expand=changelog and flows through the same
-- corrected write path new events use, which is both simpler and more trustworthy than parsing
-- back through whatever partial payloads happen to already be sitting in raw_event.
CREATE TABLE staging.jira_issue_state (
    issue_key       TEXT PRIMARY KEY,
    issue_id        TEXT NOT NULL,
    project_key     TEXT NOT NULL,
    issue_type      TEXT,
    status          TEXT,
    summary         TEXT,
    assignee        TEXT,
    created_at      TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    -- Best-effort "was this reopened after being marked done" signal for the Rework category
    -- (E5-S1) — derived from changelog status transitions matched against Jira's default
    -- terminal status names (Done/Closed/Resolved). Custom workflows with different terminal
    -- status names won't be caught by this; it's a heuristic, not authoritative, and undercounts
    -- rather than overcounts (see InvestmentProfileQueryService).
    reopened        BOOLEAN NOT NULL DEFAULT FALSE,
    last_received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_jira_issue_state_project ON staging.jira_issue_state (project_key);