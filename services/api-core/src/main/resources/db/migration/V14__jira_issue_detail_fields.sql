-- The new Jira Work Items dashboard (PRD PG — per-project issue detail, status/priority/type
-- breakdowns, "topics" via labels) needs more than V10's minimal classification projection
-- (issue_type/status/assignee) gave Investment Profile. This adds only standard Jira fields that
-- exist on every instance regardless of workflow/custom-field configuration — never a
-- customfield_XXXXX guess (those IDs are instance-specific and would violate the no-fabrication
-- policy if assumed). Story points / epic link are deliberately NOT added here for the same
-- reason: both live behind unpredictable custom field IDs.
--
-- status_category mirrors Jira's own three-value categorization (new / indeterminate / done) —
-- stable across arbitrarily renamed custom workflow statuses, unlike matching on status name the
-- way V10's `reopened` heuristic has to.
ALTER TABLE staging.jira_issue_state
    ADD COLUMN priority        TEXT,
    ADD COLUMN status_category TEXT,
    ADD COLUMN reporter        TEXT,
    ADD COLUMN labels          TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN due_date        DATE;

CREATE INDEX idx_jira_issue_state_status_category ON staging.jira_issue_state (status_category);
