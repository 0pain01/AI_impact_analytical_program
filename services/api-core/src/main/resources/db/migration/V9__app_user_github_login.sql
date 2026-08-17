-- Backs the Personal Activity tab (PRD persona table: "Individual Contributor... Personal
-- Activity (opt-in)... Self only"). Self-scoping requires knowing which staging.pull_request_state
-- author / staging.pull_request_review_state reviewer_login corresponds to a logged-in app_user
-- — core.app_user had no such link at all until now, which is exactly why IC accounts had
-- nothing to see (SecurityConfig denied them the shared /api/v1/metrics/** routes outright,
-- since those aren't self-scoped and there was no self-scoped route to send them to instead).
ALTER TABLE core.app_user ADD COLUMN github_login TEXT;