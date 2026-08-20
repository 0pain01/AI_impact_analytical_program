-- "Last sync" on the Admin Connectors panel was derived purely from MAX(received_at) over
-- staging.raw_event, which only advances when ingestion-writer's idempotent insert actually
-- lands a NEW row (ON CONFLICT ... DO NOTHING skips exact repeats of an already-seen entity
-- version). A connector that runs successfully but finds nothing new (e.g. Jira re-checking
-- issues nobody has touched since the last sync) correctly writes zero new rows — so its "last
-- sync" timestamp sits frozen, making a healthy, actively-working connector look stale/dead.
--
-- This table records "last time we heard from this source at all" — updated on EVERY event
-- StagingEventWriter processes, whether it turns out to be a duplicate or genuinely new —
-- independent of staging.raw_event's "last time something actually changed" signal. Both are
-- useful and answer different questions; the Admin UI now shows both rather than conflating them.
CREATE TABLE staging.connector_activity (
    source           TEXT PRIMARY KEY,
    last_checked_at  TIMESTAMPTZ NOT NULL
);
