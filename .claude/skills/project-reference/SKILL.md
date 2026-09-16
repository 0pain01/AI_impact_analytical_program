---
name: project-reference
description: Answer any question about the AI Impact Evaluation platform that isn't "how is the code structured" — what a service/API/screen does, what calls what (frontend↔backend, backend↔docker), what credentials/resources something needs, how to run or deploy it, what's built vs. planned, what a metric means, business/product questions, security/RBAC/compliance posture. Trigger on questions from a business owner, PM, or SWE about how the project works, what it needs, or its current state — not on "explain this function" (just read the file) or "how does X connect to Y in the codebase" (that's /graphify).
---

# /project-reference

Answers **any** question about this project other than raw code-structure questions — the kind a
business owner, a PM, or a SWE joining the project would ask. This is reference-lookup against
already-written, already-accurate docs, not exploration: it should be cheap (a few file reads),
not a `/graphify` traversal (2000+ tokens per query, and the wrong tool shape for "what
credentials does X need" or "what does this button call" — those aren't graph-traversal
questions, they're table lookups).

## Route the question to the right doc — don't guess, don't re-derive from source if a doc answers it

| Question is about... | Read this first |
|---|---|
| What a service does, why it's separate, what it depends on, its port | `docs/04-operations/deployment-guide.md` §4 |
| Every connector: source, live vs. backfill path, what table it feeds | `deployment-guide.md` §4.7 |
| Infrastructure: Postgres schemas, RabbitMQ topology, what's containerized and why | `deployment-guide.md` §5 |
| **Credentials/secrets needed to run or hand off the project** | `deployment-guide.md` §7.1 (per-integration: what to generate, where) and §7.3 (consolidated env-var table, every var × every service) |
| Is it production-ready / SOC2-ready / secrets-manager-integrated | `deployment-guide.md` §7.4 — answer honestly, it's pilot-shaped today, and that's a named list of deliberate deferrals, not an oversight |
| **What API a screen/button calls, and what happens after** (frontend→backend), or the reverse (**what UI feature a given endpoint/table serves**) | `docs/04-operations/frontend-backend-map.md` — organized by screen, one table per screen, plus a Docker↔backend wiring section (§12) and a reverse business/PM index (§13) |
| The full formal API surface (every endpoint, method, auth role) | `docs/03-architecture/technical-specification.md` §5 — this is ground truth; the OpenAPI spec at `services/api-core/src/main/resources/openapi/api-core.yml` has historically drifted behind it, say so if asked which is authoritative |
| Data model — what's in `staging`/`core`/`mart`, which table who writes/reads | `technical-specification.md` §4 |
| **How a specific metric is computed** (formula, edge cases, what BRD requirement it satisfies) | `docs/01-product/metric-definitions.md` — this is the metric source of truth, published in-app too |
| What the product does, who it's for, the five roles, what each dashboard is *for* in product terms | `docs/01-product/functional-specification.md` |
| What's actually shipped vs. still planned, epic by epic | `docs/01-product/prd.md` Appendix B (delivery status: ✅/🟡/⬜ per story) |
| What's explicitly out of scope this version | `functional-specification.md` §6 |
| Non-negotiable product rules (no surveillance, no manual tagging, least-privilege, auditability) | `functional-specification.md` §2, or root `CLAUDE.md` |
| Why a specific technical choice was made (a framework, datastore, queue, containerization decision) | `docs/03-architecture/decisions/` — the ADRs, one per decision, numbered; check the newest ADR on a topic in case an older one was superseded |
| Security architecture: RBAC roles, JWT, audit log retention, what's TLS/encrypted | `technical-specification.md` §7, and `docs/02-standards/security-and-privacy-standards.md` |
| Deploying to real cloud infrastructure, step by step, deployment order | `deployment-guide.md` §7.2 |
| AWS-specific sizing/cost for a small team | `docs/04-operations/aws-deployment-plan.md` |
| *Why* something was added, in narrative/historical order | `deployment-guide.md` §8, or `docs/CHANGELOG.md` for the newest changes specifically |
| One service's own exact config/API surface, when a doc above is stale or too summarized | That service's own `README.md` (`services/<service>/README.md` or `services/connectors/connector-<name>/README.md`) — per `deployment-guide.md`'s own stated policy, the README wins when they disagree |

If two sources genuinely conflict, say so and name which one looks stale — don't silently pick
one. If nothing above answers the question, say that plainly (no-fabrication policy) rather than
guessing, read the actual source as a last resort, and consider offering to update the doc that
should have had the answer.

## When this isn't the right skill

- "How does X connect to Y in the codebase" / "what would break if I changed this function" /
  "is this module too tangled, should it be split" / "find every caller of this symbol" —
  structural questions about the code's own shape — that's `/graphify`'s job.
- "Explain this specific file/function" — just read the file, no doc lookup needed.
- A question about a different repository — this skill is scoped to this platform's own docs.
