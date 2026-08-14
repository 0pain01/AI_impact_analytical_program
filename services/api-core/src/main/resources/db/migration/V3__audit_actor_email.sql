-- Record the acting JWT principal by email without requiring an app_user row (ADR-0004).
-- The audit log stays append-only; no UPDATE/DELETE is ever issued against it.
ALTER TABLE core.audit_log ADD COLUMN actor_email TEXT;
CREATE INDEX idx_audit_actor_email ON core.audit_log (actor_email);
