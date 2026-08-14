-- Backs the Code Review tab (review load per reviewer, PR cycle-stage breakdown). Reviews aren't
-- in the PR list/get payload at all — connector-github now fetches them via a separate call per
-- PR (GET /pulls/{number}/reviews) and publishes each as pull_request_review.snapshot.
--
-- repo/pr_number are extracted from the review's pull_request_url rather than passed separately
-- by the connector, so the raw event stays a self-contained, unmodified copy of GitHub's review
-- object (same principle as the other snapshot event types).
CREATE TABLE staging.pull_request_review_state (
    repo              TEXT NOT NULL,
    pr_number         BIGINT NOT NULL,
    review_id         TEXT NOT NULL,
    reviewer_login    TEXT,
    state             TEXT,          -- APPROVED / CHANGES_REQUESTED / COMMENTED / DISMISSED
    submitted_at      TIMESTAMPTZ,
    last_received_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (repo, review_id)
);
CREATE INDEX idx_pr_review_state_repo_pr ON staging.pull_request_review_state (repo, pr_number);
CREATE INDEX idx_pr_review_state_reviewer ON staging.pull_request_review_state (reviewer_login);
CREATE INDEX idx_pr_review_state_submitted_at ON staging.pull_request_review_state (submitted_at);