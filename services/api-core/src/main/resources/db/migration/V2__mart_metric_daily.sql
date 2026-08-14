-- Materialized daily metric values (ADR-0002 mart schema). Written only by the metrics
-- engine; fully rebuildable from staging. repo = '*' rows are org-level rollups.
CREATE TABLE mart.metric_daily (
    id                   BIGSERIAL PRIMARY KEY,
    metric_key           TEXT        NOT NULL,
    repo                 TEXT        NOT NULL,
    day                  DATE        NOT NULL,
    value                NUMERIC     NOT NULL,
    sample_size          INT         NOT NULL DEFAULT 0,
    metric_logic_version TEXT        NOT NULL,
    computed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_metric_daily UNIQUE (metric_key, repo, day)
);
CREATE INDEX idx_metric_daily_lookup ON mart.metric_daily (metric_key, repo, day);
