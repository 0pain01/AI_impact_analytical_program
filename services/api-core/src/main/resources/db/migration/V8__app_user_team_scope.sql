-- Backs real RBAC scope segregation (PRD E8-S1/S2, previously tracked as a known gap in
-- CockpitController's javadoc: "any analytical-role caller can pass any scope"). A user's
-- team_id becomes the source of truth for what ENG_LEADER/MANAGER accounts can see — the
-- server pins their scope to this column now, instead of trusting whatever `scope` query
-- param the client happens to send.
ALTER TABLE core.app_user ADD COLUMN team_id UUID REFERENCES core.team (id);

-- Lets the Admin console show genuine "last active" data instead of nothing.
ALTER TABLE core.app_user ADD COLUMN last_login_at TIMESTAMPTZ;