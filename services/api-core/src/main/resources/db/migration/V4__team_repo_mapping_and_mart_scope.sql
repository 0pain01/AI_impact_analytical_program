-- Team-structure import (E2-S2): map repos to teams so metrics can roll up by team.
CREATE TABLE core.team_repo (
    team_id UUID NOT NULL REFERENCES core.team (id),
    repo    TEXT NOT NULL,
    PRIMARY KEY (team_id, repo)
);
CREATE INDEX idx_team_repo_repo ON core.team_repo (repo);

-- Org → team drill-down (E4-S2): the mart's scope column previously held only a repo full
-- name or '*' (org). Rename to scope_id and add scope_type so a team UUID is unambiguous.
ALTER TABLE mart.metric_daily RENAME COLUMN repo TO scope_id;
ALTER TABLE mart.metric_daily ADD COLUMN scope_type TEXT NOT NULL DEFAULT 'repo';
UPDATE mart.metric_daily SET scope_type = 'org' WHERE scope_id = '*';
