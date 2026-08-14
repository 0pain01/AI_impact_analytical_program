# Operations — Runbooks & Deployment

Populated as services ship. **Rule:** anything that can page a human gets a runbook here
before it ships (connector outage, queue backlog/DLQ growth, ingestion lag > 15 min,
metric-recompute failure).

Runbook template: symptom → impact → diagnosis steps → remediation → escalation.

From Phase 2, the SOC 2 readiness checklist also lives here.
