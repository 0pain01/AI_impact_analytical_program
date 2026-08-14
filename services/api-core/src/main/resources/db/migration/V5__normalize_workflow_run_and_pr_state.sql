-- Performance fix: metrics-engine's recompute previously re-derived "latest state per workflow
-- run / PR" from raw JSONB via DISTINCT ON + full sort, independently in every one of its 6
-- metric queries, on every recompute cycle. That cost scales with total event volume and gets
-- paid repeatedly rather than once. staging.raw_event remains the immutable, append-only source
-- of truth (unchanged, still the replay/audit log) — these two tables are a typed, indexed
-- *current-state projection* of it, maintained incrementally by ingestion-writer as new events
-- arrive, so metrics-engine reads simple indexed columns instead of parsing+deduping JSONB.

CREATE TABLE staging.workflow_run_state (
    repo              TEXT NOT NULL,
    run_id            TEXT NOT NULL,
    conclusion        TEXT,
    name              TEXT,
    ts                TIMESTAMPTZ,
    last_received_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (repo, run_id)
);
CREATE INDEX idx_workflow_run_state_repo_ts ON staging.workflow_run_state (repo, ts);

CREATE TABLE staging.pull_request_state (
    repo              TEXT NOT NULL,
    pr_id             TEXT NOT NULL,
    created_at        TIMESTAMPTZ,
    merged_at         TIMESTAMPTZ,
    last_received_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (repo, pr_id)
);
CREATE INDEX idx_pull_request_state_merged_at ON staging.pull_request_state (merged_at)
    WHERE merged_at IS NOT NULL;

-- One-time backfill: same dedup logic metrics-engine's RUNS_CTE used to do at query time
-- (latest snapshot per run_id, by received_at), run once here instead of on every recompute.
INSERT INTO staging.workflow_run_state (repo, run_id, conclusion, name, ts, last_received_at)
SELECT repo, run_id, run ->> 'conclusion', run ->> 'name', (run ->> 'updated_at')::timestamptz, received_at
FROM (
    SELECT DISTINCT ON (repo, run_id)
           COALESCE(payload -> 'workflow_run', payload) AS run,
           COALESCE(COALESCE(payload -> 'workflow_run', payload) -> 'repository' ->> 'full_name',
                    payload -> 'repository' ->> 'full_name', 'unknown') AS repo,
           COALESCE(payload -> 'workflow_run', payload) ->> 'id' AS run_id,
           received_at
    FROM staging.raw_event
    WHERE source = 'github' AND event_type IN ('workflow_run', 'workflow_run.snapshot')
          AND COALESCE(payload -> 'workflow_run', payload) ->> 'id' IS NOT NULL
    ORDER BY repo, run_id, received_at DESC
) dedup
ON CONFLICT (repo, run_id) DO NOTHING;

-- Same backfill for PRs (same dedup logic MERGED_PRS_CTE used to do at query time).
INSERT INTO staging.pull_request_state (repo, pr_id, created_at, merged_at, last_received_at)
SELECT repo, pr_id, (p ->> 'created_at')::timestamptz, (p ->> 'merged_at')::timestamptz, received_at
FROM (
    SELECT DISTINCT ON (repo, pr_id)
           COALESCE(payload -> 'pull_request', payload) AS p,
           COALESCE(COALESCE(payload -> 'pull_request', payload) -> 'base' -> 'repo' ->> 'full_name',
                    'unknown') AS repo,
           COALESCE(payload -> 'pull_request', payload) ->> 'id' AS pr_id,
           received_at
    FROM staging.raw_event
    WHERE source = 'github' AND event_type IN ('pull_request', 'pull_request.snapshot')
          AND COALESCE(payload -> 'pull_request', payload) ->> 'id' IS NOT NULL
    ORDER BY repo, pr_id, received_at DESC
) dedup
ON CONFLICT (repo, pr_id) DO NOTHING;