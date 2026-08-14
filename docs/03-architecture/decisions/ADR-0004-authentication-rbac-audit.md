# ADR-0004: Authentication, RBAC, and audit enforcement in api-core

- **Status:** Accepted
- **Date:** 2026-07-05
- **Deciders:** Vishal, Aditi
- **BRD traceability:** FR-8.8 (RBAC + audit), NFR Security/Auditability/Data-privacy; PRD E8-S1/S2/S3

## Context

Every api-core endpoint is currently unauthenticated and flagged "dev-only". Before any real
user sees data we must enforce the five BRD roles server-side on every endpoint, keep an
append-only audit trail (≥ 12 months), and honor the privacy invariant that individual data is
opt-in and access-gated. The security standards mandate OIDC/SSO for production with short-lived
RS256 JWTs and server-side RBAC on every endpoint.

## Decision

1. **api-core is a JWT resource server** (Spring Security `oauth2ResourceServer`). Access tokens
   are RS256-signed, short-lived (15 min), and carry a `role` claim mapped to a Spring authority
   `ROLE_<name>`. Roles: `ADMIN`, `ENG_LEADER`, `MANAGER`, `IC`, `FINANCE_READONLY` (matches the
   `core.app_user` check constraint).
2. **RBAC is enforced in the `SecurityFilterChain`**, not the UI: `/api/v1/metrics/**` requires an
   analytical role (`ADMIN`, `ENG_LEADER`, `MANAGER`, `FINANCE_READONLY`); `/api/v1/audit/**`
   requires `ADMIN`; everything else authenticated. Stateless, CSRF disabled (token API).
   Individual-scoped surfaces (not yet built) will additionally require the subject's opt-in
   (`core.contributor.opted_in_personal_view`) — E8-S2 invariant recorded here so no future
   endpoint forgets it.
3. **Signing keys:** an RSA keypair provides `JwtEncoder`/`JwtDecoder`. In dev it is generated at
   startup (ephemeral — restarts invalidate tokens, acceptable). Production supplies a managed key
   (secret manager) and, once an external IdP lands, validates the IdP's JWKS instead of issuing
   tokens locally.
4. **Dev-token bridge:** a gated endpoint `POST /api/v1/auth/dev-token` issues a role-scoped token
   for local/pilot use, **disabled by `auth.dev-token-enabled=false` in any real environment**.
   It exists only so RBAC can be built and demonstrated ahead of the OIDC integration; every
   issuance is audited. **This is a known bridge, not the production auth path** — a superseding
   ADR will record the OIDC decision.
5. **Audit log** (`core.audit_log`, append-only): a V3 migration adds `actor_email` so the acting
   JWT principal is recorded without requiring an `app_user` row. Writes go through an `AuditLog`
   port (no UPDATE/DELETE is ever issued). Reads are `ADMIN`-only.

## Options considered

1. **Chosen: resource-server JWT + gated dev-token bridge** — matches the security standards,
   keeps the OIDC swap to a bean change, lets RBAC ship and be tested now.
2. **Full OIDC/IdP integration now** — correct end state but blocks the whole slice on IdP
   procurement/config (OQ-7-adjacent); disproportionate for a pilot with no external users yet.
3. **Username/password + session cookies** — contradicts the stateless, no-static-credential
   posture; more attack surface; still needs replacing for SSO.

## Consequences

- RBAC is real and enforced server-side; the frontend can no longer read metrics without a token
  (a dev bootstrap acquires one locally; production uses the login/OIDC flow).
- The dev-token endpoint is a deliberate, audited, config-gated hole for pilot only — it MUST be
  disabled in production; tracked as a release-gate item.
- Ephemeral dev keys mean tokens don't survive an api-core restart in dev — fine locally.
- Audit tamper-evidence is currently "append-only + access-controlled"; cryptographic chaining is
  a later hardening, noted in PRD Appendix B.
