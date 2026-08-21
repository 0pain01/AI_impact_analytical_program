-- PRD E9, AI-04: adds an ai_assisted flag to pull_request_state so AiCostTrackQueryService can
-- segment cycle time by AI attribution — the missing piece for "AI-assisted vs. non-AI delta".
-- Detected from PR title/body/label text against known AI coding-assistant trailer/signature
-- conventions (Claude Code's "Co-authored-by: Claude" / "Generated with Claude Code", GitHub
-- Copilot's coding-agent equivalents) — the same heuristic already documented for the
-- "AI-assisted commits (F8)" supporting metric in metric-definitions.md, applied here to PRs
-- instead of commits (a PR's body/labels are already in the ingested payload; per-PR commit
-- lists would need a separate API call the connector doesn't make today).
ALTER TABLE staging.pull_request_state
    ADD COLUMN ai_assisted BOOLEAN NOT NULL DEFAULT false;

-- One-time backfill for rows that predate this migration, same dedup-then-derive pattern V7 used.
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
SET ai_assisted = (
    COALESCE(dedup.p ->> 'title', '') || ' ' || COALESCE(dedup.p ->> 'body', '') || ' ' ||
    COALESCE((SELECT string_agg(l ->> 'name', ' ') FROM jsonb_array_elements(
        CASE WHEN jsonb_typeof(dedup.p -> 'labels') = 'array' THEN dedup.p -> 'labels' ELSE '[]'::jsonb END
    ) l), '')
) ~* '(co-authored-by:\s*(claude|copilot|cursor)|generated (with|by)\s*(claude( code)?|copilot|cursor)|claude[- ]assisted|copilot[- ]assisted)'
FROM dedup
WHERE s.repo = dedup.repo AND s.pr_id = dedup.pr_id;
