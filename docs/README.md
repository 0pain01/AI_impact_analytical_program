# AI Impact Evaluation Documentation Index

All project documentation lives here. Documentation is part of the definition of done —
see the Documentation Policy in the root [CLAUDE.md](../CLAUDE.md).

| Section | Contents |
|---|---|
| [01-product/](01-product/) | BRD summary, requirements traceability, metric definitions |
| [02-standards/](02-standards/) | Engineering standards, security & privacy standards |
| [03-architecture/](03-architecture/) | System architecture (C4), data model, ADRs |
| [04-operations/](04-operations/) | Runbooks, deployment, monitoring (populated as services ship) |
| [CHANGELOG.md](CHANGELOG.md) | One-line log of user-visible / architecturally significant changes |

## Key documents

- **What we're building & why:** [01-product/brd-summary.md](01-product/brd-summary.md)
- **Product requirements (PRD v1.0, epics E1–E11):** [01-product/prd.md](01-product/prd.md)
  (mirrors the signed [PRD docx](01-product/AI_Impact_Evaluation_PRD_v1.0.docx); includes delivery status)
- **Metric formulas (source of truth):** [01-product/metric-definitions.md](01-product/metric-definitions.md)
- **How we build it:** [02-standards/engineering-standards.md](02-standards/engineering-standards.md)
- **Security & privacy rules:** [02-standards/security-and-privacy-standards.md](02-standards/security-and-privacy-standards.md)
- **System architecture:** [03-architecture/system-architecture.md](03-architecture/system-architecture.md)
- **Decision log (ADRs):** [03-architecture/decisions/](03-architecture/decisions/)

## Documentation rules (enforced)

1. Code and docs change together — same PR, or the PR is incomplete.
2. Decisions get ADRs before/with implementation; supersede, never silently deviate.
3. Every metric shipped has a written definition (formula, sources, edge cases).
4. Every service has a README (run, test, configure, API surface).
5. Architecture diagrams reflect reality — update them when topology changes.
