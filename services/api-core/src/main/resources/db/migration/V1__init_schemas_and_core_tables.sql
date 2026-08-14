-- Layered schemas per ADR-0002: staging (immutable raw), core (normalized), mart (metrics).
CREATE SCHEMA IF NOT EXISTS staging;
CREATE SCHEMA IF NOT EXISTS mart;

-- staging: append-only raw event envelope; corrections happen downstream, never here.
CREATE TABLE staging.raw_event (
    id                BIGSERIAL PRIMARY KEY,
    source            TEXT        NOT NULL,
    source_id         TEXT        NOT NULL,
    event_type        TEXT        NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    connector_version TEXT        NOT NULL,
    payload           JSONB       NOT NULL,
    CONSTRAINT uq_raw_event_natural_key UNIQUE (source, source_id, event_type)
);
CREATE INDEX idx_raw_event_source_received ON staging.raw_event (source, received_at);

CREATE TABLE core.app_user (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email        TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    role         TEXT NOT NULL CHECK (role IN ('ADMIN', 'ENG_LEADER', 'MANAGER', 'IC', 'FINANCE_READONLY')),
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE core.team (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           TEXT NOT NULL,
    parent_team_id UUID REFERENCES core.team (id),
    source         TEXT,
    source_id      TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_team_source UNIQUE (source, source_id)
);

-- A contributor is the canonical person; aliases are their identities in each tool (FR-1.4).
CREATE TABLE core.contributor (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_name TEXT NOT NULL,
    canonical_email TEXT,
    is_bot         BOOLEAN NOT NULL DEFAULT FALSE,
    opted_in_personal_view BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE core.contributor_alias (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contributor_id UUID NOT NULL REFERENCES core.contributor (id),
    source         TEXT NOT NULL,
    source_user_id TEXT NOT NULL,
    name           TEXT,
    email          TEXT,
    match_confidence NUMERIC(3, 2),
    manually_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_alias_per_source UNIQUE (source, source_user_id)
);
CREATE INDEX idx_alias_contributor ON core.contributor_alias (contributor_id);

CREATE TABLE core.team_member (
    team_id        UUID NOT NULL REFERENCES core.team (id),
    contributor_id UUID NOT NULL REFERENCES core.contributor (id),
    PRIMARY KEY (team_id, contributor_id)
);

-- Append-only audit log (NFR Auditability): no UPDATE/DELETE is ever issued against this table.
CREATE TABLE core.audit_log (
    id            BIGSERIAL PRIMARY KEY,
    actor_user_id UUID REFERENCES core.app_user (id),
    action        TEXT NOT NULL,
    target_type   TEXT NOT NULL,
    target_id     TEXT,
    before_state  JSONB,
    after_state   JSONB,
    source_ip     TEXT,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_occurred ON core.audit_log (occurred_at);
