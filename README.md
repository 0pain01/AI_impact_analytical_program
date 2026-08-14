# AI Impact Evaluation

> **Engineering Intelligence & Analytics Platform**

[![Status](https://img.shields.io/badge/status-Phase%200%20Complete-blue)](https://github.com/0pain01/AI_impact_analytical_program)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-strict-blue)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)](https://www.postgresql.org/)

AI Impact Evaluation is an AI-native **Software Engineering Intelligence (SEI)** platform that unifies data from source control, ticketing, CI/CD, code quality, incidents, and AI coding assistants into DORA metrics, delivery analytics, and AI ROI reporting — delivered through role-based dashboards.

---

## 📋 Table of Contents

- [Features](#features)
- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Development Guide](#development-guide)
- [Project Structure](#project-structure)
- [Documentation](#documentation)
- [Golden Rules](#golden-rules)
- [Contributing](#contributing)
- [License](#license)

---

## ✨ Features

### Data Integration
- **Source Control:** GitHub / GitLab connectors
- **Project Management:** Jira integration
- **CI/CD:** GitHub Actions / Jenkins
- **Code Quality:** SonarQube
- **Incident Management:** PagerDuty
- **AI Coding Assistants:** Copilot, Cursor, Claude Code telemetry

### Analytics & Metrics
- **DORA Metrics:** Deployment frequency, Lead time, Change failure rate, Mean time to restore
- **Investment & Time Allocation Analytics**
- **Pull Request Review Analytics**
- **AI ROI Reporting** — measure the impact of AI coding assistants

### Role-Based Dashboards
- **Executive:** High-level organizational health and ROI
- **Manager:** Team-level delivery and productivity insights
- **IC (Individual Contributor):** Personal growth and contribution metrics

### Core Principles
- ✅ **Zero surveillance** — No keystroke tracking, idle-time monitoring, or individual surveillance metrics
- ✅ **Fully automated** — Metrics computed automatically; engineers never change how they work
- ✅ **Auditable** — Every configuration change, access grant, and data export is logged

---

## 🏗️ Architecture Overview

The platform follows a **message-queue-isolated** microservices architecture:

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend (React)                        │
│                    Role-based Dashboards (UI)                   │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API Core (Spring Boot)                     │
│               Auth, RBAC, Dashboards API, OpenAPI               │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Metrics Engine                             │
│          DORA, Lead Time, Investment Profile, AI ROI            │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────┐
│                    Message Queue (RabbitMQ)                     │
└───────────────┬─────────────┼─────────────┬─────────────────────┘
                │             │             │
                ▼             ▼             ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────────────┐
│  Connector:     │ │  Connector:     │ │  Connector:             │
│  GitHub         │ │  Jira           │ │  AI Telemetry           │
└─────────────────┘ └─────────────────┘ └─────────────────────────┘
                │             │             │
                └─────────────┼─────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PostgreSQL (Transactional)                   │
│              Staging → Core → Mart schemas                      │
└─────────────────────────────────────────────────────────────────┘
```

> **Key Architectural Decisions:**
> - Connectors are **isolated behind the queue** — vendor outages never lose data
> - **Contract-first API** — OpenAPI specs are the source of truth
> - All metrics are **computed from tool data** — no manual tagging required

For detailed C4 diagrams and data flows, see [System Architecture](docs/03-architecture/system-architecture.md).

---

## 🧰 Tech Stack

| Layer | Technology |
|-------|------------|
| **Frontend** | React 18 + TypeScript (strict) + Tailwind CSS + Vite |
| **Backend** | Java 21 + Spring Boot 3.x |
| **Database** | PostgreSQL 16 (transactional) |
| **Message Queue** | RabbitMQ 3 |
| **Infrastructure** | Docker Compose (local), IaC (production) |
| **Build** | Maven (backend), npm (frontend) |
| **API** | OpenAPI 3 (contract-first) |
| **Testing** | JUnit + Mockito (backend), Vitest (frontend) |
| **CI/CD** | GitHub Actions (planned) |

---

## 🚀 Getting Started

### Prerequisites

- **Docker Desktop** (or compatible container runtime)
- **JDK 21+** — e.g., `brew install openjdk` (macOS) or download from [Adoptium](https://adoptium.net/)
- **Node.js 20+** — e.g., `brew install node` (macOS) or download from [nodejs.org](https://nodejs.org/)
- **Git**

### Quick Start (Local Development)

1. **Clone the repository:**
   ```bash
   git clone https://github.com/0pain01/AI_impact_analytical_program.git
   cd AI_impact_analytical_program
   ```

2. **Start local infrastructure (PostgreSQL + RabbitMQ):**
   ```bash
   cd infra
   cp .env.example .env   # first time only
   docker compose up -d
   ```
   This provides:
   - PostgreSQL 16 at `localhost:5442` (db: `aiimpacteval`)
   - RabbitMQ 3 at `localhost:5672` (AMQP) and `localhost:15672` (management UI)

3. **Start the backend services:**
   ```bash
   ./infra/start-backend.sh
   ```
   This builds and starts all 6 backend services.

4. **Start the frontend:**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
   The UI will be available at `http://localhost:5173`

5. **Stop services:**
   ```bash
   ./infra/stop-backend.sh          # stops backend services only
   docker compose -f infra/docker-compose.yml down   # stops infrastructure too
   ```

### Running a Single Service

While iterating on a specific service (e.g., API core):
```bash
cd services
mvn spring-boot:run -pl api-core
```
Local defaults are wired so no configuration is needed beyond `.env`.

### GitHub Connector

To run the GitHub connector locally:
```bash
GITHUB_TOKEN=<your-token> mvn spring-boot:run -pl connectors/connector-github
```
> ⚠️ **Never commit `GITHUB_TOKEN`** — use environment variables or a secret manager.

### Smoke Test

After building with `mvn package` in `services/`:
```bash
./infra/smoke-e2e.sh
```
This verifies the FR-1.8 / PRD F1 validation path.

---

## 💻 Development Guide

### Backend

- **Java 21** with **Spring Boot**
- **Maven** for build and dependency management
- **Flyway** for database migrations
- **OpenAPI** for contract-first API development

**Build all services:**
```bash
cd services
mvn clean package
```

**Run tests:**
```bash
mvn test
```

### Frontend

- **React 18** with **TypeScript** (strict mode)
- **Tailwind CSS** for styling
- **Vite** for build tooling
- **OpenAPI-generated client** — no hand-written fetch types

**Development:**
```bash
cd frontend
npm install
npm run dev        # starts dev server at :5173
```

**Build:**
```bash
npm run build      # tsc -b (strict) + vite build
```

**Lint:**
```bash
npm run lint       # eslint
```

### Standards

- **Git:** Trunk-based with short-lived feature branches
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`)
- **PRs:** Required — no direct pushes to `main`; keep PRs small (< ~400 lines where feasible)
- **Testing:** Unit tests required for all business logic; integration tests for connectors
- **Security:** No secrets in code — env vars / secret manager only; TLS in transit; OWASP Top 10 review

---

## 📁 Project Structure

```
AI_impact_analytical_program/
├── .claude/                     # AI agent rules and policies
├── docs/
│   ├── 01-product/              # BRD summary, PRD, metric definitions
│   ├── 02-standards/            # Engineering & security standards
│   ├── 03-architecture/         # System architecture, C4 diagrams, ADRs
│   ├── 04-operations/           # Runbooks, deployment, monitoring
│   └── CHANGELOG.md             # User-visible / architecturally significant changes
├── frontend/                    # React + TypeScript dashboard app
│   ├── src/
│   ├── public/
│   └── package.json
├── infra/                       # Infrastructure as Code, Docker Compose
│   ├── docker-compose.yml
│   ├── .env.example
│   ├── start-backend.sh
│   └── smoke-e2e.sh
├── services/                    # Backend microservices
│   ├── api-core/                # Spring Boot API layer (auth, RBAC, dashboards)
│   ├── metrics-engine/          # DORA, lead time, investment profile computation
│   ├── identity-service/        # Contributor identity & team normalization
│   ├── ingestion-writer/        # Event ingestion and persistence
│   ├── connectors/              # One service per external tool
│   │   ├── connector-github/
│   │   ├── connector-jira/
│   │   └── ...
│   ├── platform-common/         # Shared libraries and utilities
│   └── pom.xml                  # Maven parent POM
├── templates/                   # Project templates
├── CLAUDE.md                    # Rules for AI agents (mandatory documentation policy)
├── README.md                    # This file
└── .gitignore
```

---

## 📚 Documentation

All project documentation lives in the `docs/` directory. Documentation is **part of the definition of done**.

| Document | Purpose |
|----------|---------|
| [BRD Summary](docs/01-product/brd-summary.md) | What we're building and why (condensed BRD) |
| [PRD v1.0](docs/01-product/prd.md) | Product requirements (epics E1–E11 with acceptance criteria) |
| [Metric Definitions](docs/01-product/metric-definitions.md) | Formulas, data sources, and edge cases for all metrics |
| [Engineering Standards](docs/02-standards/engineering-standards.md) | Coding, git, testing, API, and data standards |
| [Security & Privacy Standards](docs/02-standards/security-and-privacy-standards.md) | Security, privacy, and audit requirements |
| [System Architecture](docs/03-architecture/system-architecture.md) | C4 diagrams, data flows, component tables |
| [ADRs](docs/03-architecture/decisions/) | Architecture Decision Records — numbered sequentially |
| [Runbooks](docs/04-operations/) | On-call runbooks for ingestion failures, connector outages, etc. |
| [CHANGELOG](docs/CHANGELOG.md) | One-line log of user-visible / architecturally significant changes |

### Documentation Policy (Enforced)

1. **Code and docs change together** — same PR, or the PR is incomplete
2. **Decisions get ADRs** — before/with implementation; never silently deviate
3. **Every metric has a written definition** — formula, sources, edge cases
4. **Every service has a README** — run, test, configure, API surface
5. **Architecture diagrams reflect reality** — update them when topology changes

---

## 🥇 Golden Rules

1. **No surveillance features, ever** — This is an explicit ethical exclusion (BRD §5.3). Never implement keystroke tracking, idle-time monitoring, screen monitoring, or any individual-level surveillance metric. Individual activity views are opt-in and framed around growth, not monitoring.

2. **Metrics are computed automatically** — Engineers never change how they work for us. No manual tagging dependency. All metrics must be derived automatically from tool data.

3. **Docs change with code, in the same PR** — Decisions get ADRs. Documentation is part of the definition of done.

4. **Connectors are isolated behind the queue** — Vendor outages never lose data. Connectors must tolerate API failures with retry/backoff, dead-letter queues, and idempotent ingestion.

5. **Least-privilege integrations** — Request the minimum OAuth/API scopes needed per connector.

6. **Auditability** — Every configuration change, access grant, and data export must be written to the audit log (12+ month retention).

---

## 🤝 Contributing

We welcome contributions! Please follow these guidelines:

1. **Read the docs first** — Start with [BRD Summary](docs/01-product/brd-summary.md) and [Engineering Standards](docs/02-standards/engineering-standards.md). Code that contradicts them will be rejected.

2. **Create an issue** — Discuss the change before implementing.

3. **Create a feature branch** — Use short-lived branches from `main`.

4. **Write tests** — Unit tests for business logic; integration tests for connectors.

5. **Update documentation** — Docs change with code, in the same PR.

6. **Add an ADR for decisions** — Any choice of framework, datastore, queue, protocol, or third-party service must be recorded.

7. **Open a PR** — Keep PRs small (< ~400 lines where feasible).

8. **Reference requirements** — Use requirement IDs (FR-x.x, BO-x) in PR descriptions.

9. **Pass CI** — No merge with failing or skipped tests.

---

## 📄 License

This project is proprietary and confidential. Unauthorized copying, distribution, or use is strictly prohibited.

---

## 📬 Contact

- **Author:** [0pain01](https://github.com/0pain01)
- **Repository:** [AI Impact Evaluation](https://github.com/0pain01/AI_impact_analytical_program)

---

## 🙏 Acknowledgments

Built with:
- [Spring Boot](https://spring.io/projects/spring-boot)
- [React](https://react.dev/)
- [PostgreSQL](https://www.postgresql.org/)
- [RabbitMQ](https://www.rabbitmq.com/)
- [Docker](https://www.docker.com/)

---

**Status:** Phase 0 complete — architecture, standards, PRD, metric definitions, and a buildable monorepo scaffold. Phase 1 MVP implementation is next (F1: GitHub connector).
