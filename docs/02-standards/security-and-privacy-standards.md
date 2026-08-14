# Security & Privacy Standards — AI Impact Evaluation

Binding for all contributors. AI Impact Evaluation handles sensitive engineering-organization data and is
targeting SOC 2 Type II readiness (BRD NFR Security, Phase 2/3). Security is designed in from
Phase 1, not retrofitted.

## 1. Privacy-by-design (ethical constraints — permanent)

These come from BRD §5.3 and §13 and are **product invariants**, not preferences:

1. **Never build surveillance features**: no keystroke logging, idle-time tracking, screen or
   activity monitoring, or any metric whose primary purpose is watching an individual.
2. **Individual views are opt-in** and framed around growth. Default visibility is team-level
   and above. Drill-down to individual level is permission-gated (BRD FR-8.3.3).
3. **Metric transparency**: every metric shown to users links to its published definition.
   No black-box scores of people.
4. **Data minimization**: ingest only what metrics require. We do not store source code
   contents, commit diffs' payloads, Slack message bodies, or AI prompt contents — only
   metadata and derived signals.

## 2. Authentication & authorization

- **AuthN:** OIDC/SSO for production; JWT access tokens (short-lived, ≤ 15 min) + refresh
  tokens; tokens signed RS256; no long-lived static API keys for users.
- **AuthZ:** RBAC with the BRD roles — `ADMIN`, `ENG_LEADER`, `MANAGER`, `IC`,
  `FINANCE_READONLY`. Enforced **server-side on every endpoint**; the UI hides, the API denies.
- Team/individual data-visibility rules are data-level filters applied in the API layer,
  configurable by Admin (FR-8.8.2) — never implemented as frontend-only filtering.
- Multi-tenancy (Phase 4): tenant ID on every row; enforced via mandatory query-scoping
  (e.g., Postgres RLS), not application discipline alone.

## 3. Secrets & third-party credentials

- No secrets in code, config files, or CI logs — ever. Secret manager (or env vars locally
  via untracked `.env`; `.env*` is gitignored).
- Connector OAuth tokens encrypted at rest (envelope encryption), scoped least-privilege
  (read-only scopes; e.g., GitHub App with `contents:read`, `pull_requests:read`,
  `actions:read` only), rotated on schedule, revoked on connector deletion.
- Webhook endpoints verify signatures (e.g., GitHub HMAC) and reject unsigned payloads.

## 4. Data protection

- TLS 1.2+ everywhere in transit; AES-256 at rest (DB, queue, backups).
- PII inventory maintained: contributor names, emails, tool user IDs. Retention: raw events
  retained per data-retention policy (default 24 months); contributor PII erasable on request
  (identity records anonymizable without breaking aggregate metrics).
- Backups encrypted, tested restores quarterly.
- Data residency: architecture must keep the ingest path separable so a VPC/on-prem ingest
  agent (Phase 3) is possible without re-architecture.

## 5. Secure development

- Every endpoint reviewed against OWASP Top 10 & OWASP ASVS L2 before merge.
- Input validation on all external payloads (vendor webhooks are untrusted input).
- Dependency scanning (SCA) + SAST in CI; critical vulns block merge; renovate/dependabot on.
- No `latest` image tags; images pinned and scanned.
- AI-agent code contributions follow the same review gates as human code — no exception.
- LLM components (Phase 3 classification/review agent): prompts must never receive secrets or
  full source files beyond the diff under review; vendor data-retention terms reviewed before
  any LLM API is adopted (ADR required).

## 6. Audit & compliance (BRD NFR Auditability)

- Audit log for: configuration changes, access grants/revocations, role changes, data exports,
  connector connect/disconnect. Append-only, retained ≥ 12 months, queryable by Admin.
- Audit events are structured (actor, action, target, timestamp, source IP, before/after).
- Access reviews quarterly once multi-team rollout begins.
- SOC 2 readiness checklist maintained in `docs/04-operations/` from Phase 2.

## 7. Security checklist per PR

- [ ] No secrets or tokens introduced
- [ ] AuthZ enforced server-side for new/changed endpoints
- [ ] External inputs validated; webhook signatures verified
- [ ] No new PII collected without updating the PII inventory
- [ ] Audit events emitted for admin/config/export actions
- [ ] Dependencies scanned clean
