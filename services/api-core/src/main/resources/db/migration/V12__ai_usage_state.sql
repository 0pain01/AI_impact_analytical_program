-- Typed "latest state" projection for AI coding-assistant usage (PRD E9, AI-01/AI-02/AI-03),
-- same pattern as V5/V6/V10's workflow_run_state/pull_request_review_state/jira_issue_state —
-- staging.raw_event stays the source of truth, this is what the AI Cost Track query service
-- actually reads. One row per (source, actor_key, day): source is 'claude_code' or 'copilot',
-- actor_key is the connector's natural per-user identifier for that tool (Claude Code's report
-- keys by email; Copilot's keys by GitHub login — they are NOT assumed to be the same person
-- across tools without an explicit identity-resolution step, which doesn't exist for AI telemetry
-- yet; see AiCostTrackQueryService).
CREATE TABLE staging.ai_usage_state (
    source                TEXT NOT NULL,
    actor_key             TEXT NOT NULL,
    day                   DATE NOT NULL,
    sessions              INT,
    loc_added             INT,
    loc_removed           INT,
    commits               INT,
    prs                   INT,
    -- Real per-day cost for metered tools (Claude Code); a modeled per-seat allocation for
    -- flat-fee tools with no per-request cost data (Copilot) — see AI-01's documented edge case
    -- in metric-definitions.md. Never fabricated for a day with zero activity.
    cost_usd              NUMERIC,
    tokens_input          BIGINT,
    tokens_output         BIGINT,
    prompts               INT,
    requests              INT,
    accepted_suggestions  INT,
    rejected_suggestions  INT,
    -- Claude Code: terminal_type (e.g. "tmux", "iTerm.app"). Copilot: primary IDE for the day.
    primary_surface       TEXT,
    last_received_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (source, actor_key, day)
);
CREATE INDEX idx_ai_usage_state_day ON staging.ai_usage_state (day);
