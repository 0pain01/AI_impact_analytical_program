# AI Impact Evaluation Documentation Index

All project documentation lives here. Documentation is part of the definition of done —
see the Documentation Policy in the root [CLAUDE.md](../CLAUDE.md).

| Section | Contents |
|---|---|
| [01-product/](01-product/) | BRD summary, [functional specification](01-product/functional-specification.md), requirements traceability, metric definitions |
| [02-standards/](02-standards/) | Engineering standards, security & privacy standards |
| [03-architecture/](03-architecture/) | System architecture (C4), [technical specification](03-architecture/technical-specification.md), data model, ADRs |
| [04-operations/](04-operations/) | Runbooks, [deployment guide](04-operations/deployment-guide.md), [frontend↔backend call map](04-operations/frontend-backend-map.md), [AWS deployment plan](04-operations/aws-deployment-plan.md), monitoring |
| [CHANGELOG.md](CHANGELOG.md) | One-line log of user-visible / architecturally significant changes |

## Key documents

- **What we're building & why:** [01-product/brd-summary.md](01-product/brd-summary.md)
- **Product requirements (PRD v1.0, epics E1–E11):** [01-product/prd.md](01-product/prd.md)
  (mirrors the signed [PRD docx](01-product/AI_Impact_Evaluation_PRD_v1.0.docx); includes delivery status)
- **Functional specification (what the product does, module by module):** [01-product/functional-specification.md](01-product/functional-specification.md)
- **Metric formulas (source of truth):** [01-product/metric-definitions.md](01-product/metric-definitions.md)
- **How we build it:** [02-standards/engineering-standards.md](02-standards/engineering-standards.md)
- **Security & privacy rules:** [02-standards/security-and-privacy-standards.md](02-standards/security-and-privacy-standards.md)
- **System architecture:** [03-architecture/system-architecture.md](03-architecture/system-architecture.md)
- **Technical specification (data model, API contract, algorithms, security mechanics):** [03-architecture/technical-specification.md](03-architecture/technical-specification.md)
- **Decision log (ADRs):** [03-architecture/decisions/](03-architecture/decisions/)
- **What each service does, how they connect, Docker images, and the full cloud-deployment walkthrough:** [04-operations/deployment-guide.md](04-operations/deployment-guide.md)
- **Every screen's API calls and what happens downstream ("what triggers what"), both directions:** [04-operations/frontend-backend-map.md](04-operations/frontend-backend-map.md)
- **AWS deployment plan, sized and costed for a ≤20-developer team:** [04-operations/aws-deployment-plan.md](04-operations/aws-deployment-plan.md)

## Documentation rules (enforced)

1. Code and docs change together — same PR, or the PR is incomplete.
2. Decisions get ADRs before/with implementation; supersede, never silently deviate.
3. Every metric shipped has a written definition (formula, sources, edge cases).
4. Every service has a README (run, test, configure, API surface).
5. Architecture diagrams reflect reality — update them when topology changes.
