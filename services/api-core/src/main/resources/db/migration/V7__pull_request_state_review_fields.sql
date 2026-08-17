-- Extends pull_request_state (V5) with fields the Code Review tab needs (Aging PRs table) and,
-- critically, with `number` — the PR number pull_request_review_state (V6) is keyed by. V5 only
-- kept GitHub's internal `id` (pr_id), which can't be joined against reviews at all; `number` is
-- what makes that join possible.
--
-- All of these fields are already present in the bulk "List pull requests" payload the connector
-- fetches (confirmed against a real payload) — no extra API calls needed to add them.
ALTER TABLE staging.pull_request_state
    ADD COLUMN number              BIGINT,
    ADD COLUMN title               TEXT,
    ADD COLUMN author              TEXT,
    ADD COLUMN html_url            TEXT,
    ADD COLUMN state               TEXT,
    ADD COLUMN requested_reviewers TEXT[];

-- Lets Code Review join pull_request_state <-> pull_request_review_state on (repo, number).
CREATE INDEX idx_pull_request_state_repo_number ON staging.pull_request_state (repo, number);

-- Lets the Aging PRs query filter to open PRs without a full table scan.
CREATE INDEX idx_pull_request_state_open ON staging.pull_request_state (repo, created_at)
    WHERE state = 'open';

-- One-time backfill for rows that predate this migration, same dedup logic V5 used
-- (latest snapshot per PR, by received_at).
WITH dedup AS (
    SELECT DISTINCT ON (repo, pr_id)
           repo, pr_id, p
    FROM (
        SELECT
            COALESCE(COALESCE(payload -> 'pull_request', payload) -> 'base' -> 'repo' ->> 'full_name',
                     'unknown') AS repo,
            COALESCE(payload -> 'pull_request', payload) ->> 'id' AS pr_id,
            COALESCE(payload -> 'pull_request', payload) AS p,
            received_at
        FROM staging.raw_event
        WHERE source = 'github' AND event_type IN ('pull_request', 'pull_request.snapshot')
              AND COALESCE(payload -> 'pull_request', payload) ->> 'id' IS NOT NULL
    ) raw
    ORDER BY repo, pr_id, received_at DESC
)
UPDATE staging.pull_request_state s
SET number              = (dedup.p ->> 'number')::bigint,
    title               = dedup.p ->> 'title',
    author              = dedup.p -> 'user' ->> 'login',
    html_url            = dedup.p ->> 'html_url',
    state               = dedup.p ->> 'state',
    requested_reviewers = ARRAY(
        SELECT jsonb_array_elements(dedup.p -> 'requested_reviewers') ->> 'login'
    )
FROM dedup
WHERE s.repo = dedup.repo AND s.pr_id = dedup.pr_id;