# Graph Report - Mallify---AI-Powered-Analytical-Platform  (2026-08-22)

## Corpus Check
- 228 files · ~197,861 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1405 nodes · 3247 edges · 97 communities (82 shown, 15 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 236 edges (avg confidence: 0.82)
- Token cost: 416,977 input · 0 output

## Community Hubs (Navigation)
- API Security & RBAC
- Admin REST Controllers
- Identity Event Extraction
- Staging Writer Integration Tests
- Frontend Dependencies
- Frontend API Types
- Connector Backfill Services
- Admin API Client
- Staging Event Writer
- Animated Backgrounds & Motion
- Cockpit Dashboard (Frontend)
- Service Entry Points
- AI Cost Track API
- Frontend Auth & App Shell
- AI Telemetry Backfill
- Code Review Analytics API
- GitHub Event Publisher
- Admin Panels (Teams/Users)
- TypeScript Config
- Admin Domain Services
- Personal Activity API
- GitHub Backfill Service
- Queue Config & Test Beans
- Connector Admin Controller
- Investment Profile API
- AppUser & Dev-Token Auth
- Event Publisher Interfaces
- System Architecture Concepts
- Connectors & Ingestion Epics
- Connector Health Service
- Admin User Service
- DORA Metrics & Cockpit
- Repo Sync Frontend
- Queue Topology & Bindings
- JDBC Audit & Team Repos
- Product Docs & ADRs
- Clock Config (Connectors)
- JDBC AppUser Repository
- Cockpit Query Service
- Jenkins/Jira Backfill
- Setup Status API
- Admin & Local Dev Setup
- Frontend Mock Data
- Jenkins Backfill Service
- Privacy & Product Rules
- AI Cost Track (Frontend)
- Code Review (Frontend)
- AI Cost Track Data (E9)
- Audit Log API
- No-Data-Loss Ingestion
- AI ROI Metrics (AI-01..05)
- Team Snapshot Parsing
- JWT RSA Key Config
- Cockpit Endpoints (E4)
- Security & Audit Standards
- Particle Field Animation
- Vercel Deploy Config
- JDBC Identity Repository
- DORA Delivery Module (E3)
- Analytics & Reporting Epics
- Engineering Standards & Docs
- Identity Service & Infra
- Personal Activity (Frontend)
- Connector RabbitMQ Config
- Connector RabbitMQ Config
- Connector RabbitMQ Config
- Connector RabbitMQ Config
- Team Event Generator
- Demo History Seeder
- Extra Teams Seeder
- E2E Smoke Test
- Logo & Brand (Public)
- Logo & Brand (Assets)
- AI Network Background
- Seed Event Generator
- Backend Start Script
- Create Team Form
- Backend Stop Script
- api-core Module
- Services Parent POM
- AI Telemetry Module
- GitHub Connector Module
- Jenkins Connector Module
- Jira Connector Module
- Identity Service Module
- Ingestion Writer Module
- Metrics Engine Module
- Platform Common Module
- OFFICE iQ Templates (Unrelated)

## God Nodes (most connected - your core abstractions)
1. `EventEnvelope` - 64 edges
2. `StagingEventWriter` - 38 edges
3. `MetricsRecomputeServiceIntegrationTest` - 27 edges
4. `authFetch()` - 25 edges
5. `AppUser` - 24 edges
6. `Role` - 24 edges
7. `ObservedIdentity` - 23 edges
8. `AdminUserService` - 20 edges
9. `AuditEvent` - 19 edges
10. `AppUserRepository` - 19 edges

## Surprising Connections (you probably didn't know these)
- `Five RBAC roles (Admin/Eng Leader/Manager/IC/Finance)` --conceptually_related_to--> `RBAC (five BRD roles)`  [INFERRED]
  docs/01-product/BRD-Summary.pdf → services/api-core/README.md
- `AI adoption & ROI in financial terms (BO-3)` --conceptually_related_to--> `AI Cost Track (AI-01..AI-05)`  [INFERRED]
  docs/01-product/BRD-Summary.pdf → services/api-core/README.md
- `E9 AI Adoption & ROI` --implements--> `AI Cost Track (AI-01..AI-05)`  [INFERRED]
  docs/01-product/PRD-v1.0.pdf → services/api-core/README.md
- `E2 Identity & Team Normalization` --implements--> `identity-service`  [INFERRED]
  docs/01-product/PRD-v1.0.pdf → services/identity-service/README.md
- `E1 Onboarding & Connectors` --implements--> `ingestion-writer`  [INFERRED]
  docs/01-product/PRD-v1.0.pdf → services/ingestion-writer/README.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Event Ingestion Pipeline (connectors → queue → writer → staging)** — docs_03_architecture_system_architecture_connector_github, docs_03_architecture_system_architecture_connector_jira, docs_03_architecture_system_architecture_message_queue, docs_03_architecture_system_architecture_ingestion_writer, docs_03_architecture_system_architecture_staging_store [EXTRACTED 1.00]
- **Layered Postgres Schemas (staging/core/mart)** — docs_03_architecture_system_architecture_staging_store, docs_03_architecture_system_architecture_core_db, docs_03_architecture_system_architecture_metrics_mart [EXTRACTED 1.00]
- **DORA Metrics Suite** — docs_01_product_metric_definitions_dora_1_deployment_frequency, docs_01_product_metric_definitions_dora_2_lead_time_for_changes, docs_01_product_metric_definitions_dora_3_change_failure_rate, docs_01_product_metric_definitions_dora_4_mttr [EXTRACTED 1.00]
- **Connector to staging ingestion pipeline** — services_connectors_connector_github_readme_connector, services_connectors_connector_jira_readme_connector, services_connectors_connector_jenkins_readme_connector, services_connectors_connector_ai_telemetry_readme_connector, services_connectors_connector_github_readme_aiimpacteval_events, services_ingestion_writer_readme_ingestion_writer, services_ingestion_writer_readme_staging_raw_event [EXTRACTED 0.90]
- **Staging to Cockpit metrics flow** — services_ingestion_writer_readme_staging_raw_event, services_metrics_engine_readme_metrics_engine, services_metrics_engine_readme_mart_metric_daily, services_api_core_src_main_resources_openapi_api_core_cockpit_metrics [INFERRED 0.85]
- **GitHub team import to team-scoped rollups** — services_connectors_connector_github_readme_team_snapshot, services_identity_service_readme_identity_service, services_identity_service_readme_core_team, services_identity_service_readme_core_team_repo, services_metrics_engine_readme_metrics_engine [EXTRACTED 0.90]

## Communities (97 total, 15 thin omitted)

### Community 0 - "API Security & RBAC"
Cohesion: 0.05
Nodes (39): JwtAuthenticationConverter, org.junit.jupiter.api.Test, org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.context.annotation.Import, org.springframework.security.config.annotation.web.builders.HttpSecurity, org.springframework.security.core.authority.SimpleGrantedAuthority, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter (+31 more)

### Community 1 - "Admin REST Controllers"
Cohesion: 0.05
Nodes (39): jakarta.servlet.http.HttpServletRequest, org.springframework.boot.autoconfigure.condition.ConditionalOnProperty, org.springframework.http.ResponseEntity, org.springframework.security.core.Authentication, org.springframework.web.bind.annotation.DeleteMapping, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.PatchMapping (+31 more)

### Community 2 - "Identity Event Extraction"
Cohesion: 0.05
Nodes (19): IdentityEventListener, IdentityRepository, IdentityResolver, ObservedIdentity, TeamImportService, TeamRepository, MemberRef, TeamSnapshot (+11 more)

### Community 3 - "Staging Writer Integration Tests"
Cohesion: 0.09
Nodes (15): java.util.concurrent.locks.ReentrantLock, org.junit.jupiter.api.BeforeAll, org.junit.jupiter.api.BeforeEach, org.springframework.scheduling.annotation.Scheduled, org.springframework.transaction.annotation.Transactional, org.testcontainers.containers.PostgreSQLContainer, org.testcontainers.junit.jupiter.Testcontainers, JdbcTemplate (+7 more)

### Community 4 - "Frontend Dependencies"
Cohesion: 0.05
Nodes (43): autoprefixer, dependencies, gsap, lenis, lucide-react, react, react-dom, recharts (+35 more)

### Community 5 - "Frontend API Types"
Cohesion: 0.06
Nodes (33): ADR-0004, AgingPr, AgingPrsPage, AiCostAssumptions, AiCostDailySpendPoint, AiCostDeveloperAllocation, AiCostImpact, AiCostKpis (+25 more)

### Community 6 - "Connector Backfill Services"
Cohesion: 0.15
Nodes (14): com.fasterxml.jackson.databind.ObjectMapper, org.slf4j.Logger, org.springframework.stereotype.Service, org.springframework.web.client.RestClient, Builder, GithubRestClients, Builder, GithubTeamBackfillService (+6 more)

### Community 7 - "Admin API Client"
Cohesion: 0.11
Nodes (29): AdminUser, AuditEntry, connectGithubOrgTeams(), ConnectorHealth, createAdminUser(), fetchAdminConnectors(), fetchAuditLog(), RepoSyncStatus (+21 more)

### Community 8 - "Staging Event Writer"
Cohesion: 0.20
Nodes (3): com.fasterxml.jackson.databind.JsonNode, RepoAndPrNumber, StagingEventWriter

### Community 9 - "Animated Backgrounds & Motion"
Cohesion: 0.11
Nodes (26): Blob, BLOBS, GradientMeshBackground(), useCanvasSize(), EASE, prefersReducedMotion(), useCountUp(), useParallax() (+18 more)

### Community 10 - "Cockpit Dashboard (Frontend)"
Cohesion: 0.11
Nodes (26): CockpitResponse, CockpitTile, fetchCockpit(), MetricKey, buildCockpitCsv(), changeFailureRateTier(), Cockpit(), CORE_DORA (+18 more)

### Community 11 - "Service Entry Points"
Cohesion: 0.10
Nodes (10): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.scheduling.annotation.EnableScheduling, ApiCoreApplication, ConnectorAiTelemetryApplication, ConnectorGithubApplication, ConnectorJenkinsApplication, ConnectorJiraApplication, IdentityServiceApplication (+2 more)

### Community 12 - "AI Cost Track API"
Cohesion: 0.18
Nodes (12): AiCostTrackDtos, AiCostTrackResponse, Assumptions, DailySpendPoint, DeveloperAllocation, ImpactMetrics, Kpis, RoiMetrics (+4 more)

### Community 13 - "Frontend Auth & App Shell"
Cohesion: 0.12
Nodes (19): getSession(), login(), logout(), readSession(), Role, Session, App(), AppShell() (+11 more)

### Community 14 - "AI Telemetry Backfill"
Cohesion: 0.14
Nodes (8): BackfillController, BackfillException, BackfillResult, ClaudeCodeUsageBackfillService, BackfillException, BackfillResult, CopilotUsageBackfillService, EventPublisher

### Community 15 - "Code Review Analytics API"
Cohesion: 0.16
Nodes (12): java.sql.Array, AgingPr, AgingPrsPage, CodeReviewDtos, CodeReviewResponse, PrCycleStage, ReviewerLoad, CodeReviewQueryService (+4 more)

### Community 16 - "GitHub Event Publisher"
Cohesion: 0.16
Nodes (10): org.springframework.amqp.rabbit.core.RabbitTemplate, org.springframework.stereotype.Component, Override, RabbitEventPublisher, Override, RabbitEventPublisher, Override, RabbitEventPublisher (+2 more)

### Community 17 - "Admin Panels (Teams/Users)"
Cohesion: 0.12
Nodes (16): deleteTeam(), fetchAdminUsers(), fetchInvestmentProfile(), fetchTeams(), InvestmentProfileResponse, Team, RepoTeamsPanel(), handleDeleteTeam() (+8 more)

### Community 18 - "TypeScript Config"
Cohesion: 0.09
Nodes (21): compilerOptions, allowImportingTsExtensions, isolatedModules, jsx, lib, module, moduleDetection, moduleResolution (+13 more)

### Community 19 - "Admin Domain Services"
Cohesion: 0.14
Nodes (7): org.springframework.security.oauth2.jwt.JwtEncoder, Builder, NoSuchTeamException, TeamAdminService, TeamHasDependentsException, AuditLog, DevTokenService

### Community 20 - "Personal Activity API"
Cohesion: 0.14
Nodes (8): OwnPr, PersonalActivityResponse, PersonalDtos, ReviewGiven, OwnPr, ReviewGiven, PersonalQueryService, AppUserRepository

### Community 21 - "GitHub Backfill Service"
Cohesion: 0.17
Nodes (7): com.fasterxml.jackson.databind.node.ArrayNode, BackfillController, BackfillResult, GithubBackfillService, PullRequestBackfillResult, TeamBackfillResult, BackfillException

### Community 22 - "Queue Config & Test Beans"
Cohesion: 0.16
Nodes (6): org.springframework.context.annotation.Bean, TestBeans, Jackson2JsonMessageConverter, QueueConfig, Jackson2JsonMessageConverter, QueueTopologyConfig

### Community 23 - "Connector Admin Controller"
Cohesion: 0.17
Nodes (8): ConnectorAdminService, RepoSyncStatus, SyncState, COMPLETED, FAILED, IN_PROGRESS, Trigger, AuditEvent

### Community 24 - "Investment Profile API"
Cohesion: 0.18
Nodes (10): CategoryCount, InvestmentProfileDtos, InvestmentProfileResponse, MonthlyBreakdown, TeamBreakdown, InvestmentProfileQueryService, CategoryCount, InvestmentProfileResponse (+2 more)

### Community 25 - "AppUser & Dev-Token Auth"
Cohesion: 0.25
Nodes (5): AppUser, DevTokenServiceTest, FakeAppUserRepository, Override, RecordingAuditLog

### Community 26 - "Event Publisher Interfaces"
Cohesion: 0.14
Nodes (8): Override, RecordingEventPublisher, EventPublisher, EventPublisher, Override, RecordingEventPublisher, EventEnvelope, EventEnvelopeTest

### Community 27 - "System Architecture Concepts"
Cohesion: 0.17
Nodes (18): Jenkins CI/CD Connector Implementation Brief, StagingEventWriter, staging.workflow_run_state (provider-agnostic table), Investment Profile (planned/unplanned/rework), ADR-0002 Queue-Isolated Connectors, Single Postgres, Layered Schemas (staging/core/mart), Queue-Isolated Connector Services, ADR-0003 Event Envelope & Queue Topology (+10 more)

### Community 28 - "Connectors & Ingestion Epics"
Cohesion: 0.17
Nodes (18): E1 Onboarding & Connectors, E2 Identity & Team Normalization, connector-ai-telemetry, connector-ai-telemetry config (usage-file seam), ADR-0002 (connectors own no business logic), ADR-0003 (idempotent event publishing), aiimpacteval.events exchange, connector-github (+10 more)

### Community 29 - "Connector Health Service"
Cohesion: 0.19
Nodes (7): AdminConnectorService, ConnectorHealth, ConnectorStatus, CONNECTED, NOT_CONNECTED, STALE, AdminController

### Community 30 - "Admin User Service"
Cohesion: 0.33
Nodes (3): AdminUserService, AppUserView, NoSuchAdminUserException

### Community 31 - "DORA Metrics & Cockpit"
Cohesion: 0.14
Nodes (15): normalizeJenkinsResult, DORA-1 Deployment Frequency, DORA-2 Lead Time for Changes, DORA-2b Ticket Lead Time, DORA-3 Change Failure Rate, DORA-4 Mean Time to Restore (MTTR), Time to Value (TTV), Executive Cockpit Dashboard (+7 more)

### Community 32 - "Repo Sync Frontend"
Cohesion: 0.27
Nodes (15): addTeamRepo(), authFetch(), connectRepo(), disconnectRepo(), fetchRepoSyncStatus(), listTeamRepos(), removeTeamRepo(), ConnectRepoForm() (+7 more)

### Community 33 - "Queue Topology & Bindings"
Cohesion: 0.34
Nodes (5): org.springframework.amqp.core.Binding, org.springframework.amqp.core.TopicExchange, org.springframework.amqp.rabbit.connection.ConnectionFactory, org.springframework.amqp.support.converter.Jackson2JsonMessageConverter, EventTopology

### Community 34 - "JDBC Audit & Team Repos"
Cohesion: 0.21
Nodes (6): org.springframework.jdbc.core.JdbcTemplate, org.springframework.stereotype.Repository, Override, JdbcAuditLog, Override, JdbcTeamRepository

### Community 35 - "Product Docs & ADRs"
Cohesion: 0.29
Nodes (14): CLAUDE.md — AI Agent Rules & Documentation Policy, Mandatory Documentation Policy, BRD Summary, Business Objectives (BO-1..BO-7), Functional Requirements Document v1.0, Metric Definitions, Product Requirements Document v1.0, Epic Map E1–E11 (+6 more)

### Community 36 - "Clock Config (Connectors)"
Cohesion: 0.21
Nodes (5): org.springframework.context.annotation.Configuration, ClockConfig, ClockConfig, ClockConfig, ClockConfig

### Community 37 - "JDBC AppUser Repository"
Cohesion: 0.27
Nodes (3): org.springframework.jdbc.core.RowMapper, Override, JdbcAppUserRepository

### Community 38 - "Cockpit Query Service"
Cohesion: 0.24
Nodes (7): CockpitDtos, CockpitResponse, CockpitTile, DailyValue, CockpitQueryService, CockpitResponse, TileSpec

### Community 39 - "Jenkins/Jira Backfill"
Cohesion: 0.21
Nodes (6): Builder, BackfillException, BackfillResult, Builder, JiraBackfillService, Builder

### Community 40 - "Setup Status API"
Cohesion: 0.29
Nodes (4): SetupController, ChecklistItem, SetupQueryService, SetupStatus

### Community 41 - "Admin & Local Dev Setup"
Cohesion: 0.22
Nodes (11): E8 Administration & Access Control, Local Development Environment, PostgreSQL 16 (aiimpacteval db), RabbitMQ 3 message broker, Project Setup Commands, ADR-0004 (RS256 JWT RBAC), api-core service, Append-only audit log (+3 more)

### Community 42 - "Frontend Mock Data"
Cohesion: 0.18
Nodes (10): admin, AgingPr, AuditEntry, codeReview, ConnectorStatus, investmentProfile, InvestmentSlice, InvestmentTrendPoint (+2 more)

### Community 43 - "Jenkins Backfill Service"
Cohesion: 0.31
Nodes (4): BackfillController, BackfillException, BackfillResult, JenkinsBackfillService

### Community 44 - "Privacy & Product Rules"
Cohesion: 0.24
Nodes (10): Non-Negotiable Product Rules, Analytics layer only (never replaces tools), BRD Summary — AI Impact Evaluation, Least-Privilege Integrations, No Manual Tagging Dependency, No Surveillance Features (ethical exclusion), Five RBAC roles (Admin/Eng Leader/Manager/IC/Finance), Vision — trustworthy real-time delivery + AI ROI view (+2 more)

### Community 45 - "AI Cost Track (Frontend)"
Cohesion: 0.24
Nodes (7): AiCostTrackResponse, fetchAiCostTrack(), AiCostTrack(), currency(), Tab, TABS, TOOL_COLORS

### Community 46 - "Code Review (Frontend)"
Cohesion: 0.24
Nodes (6): CodeReviewResponse, fetchCodeReview(), ageBadge(), CodeReview(), SortBy, SortDir

### Community 47 - "AI Cost Track Data (E9)"
Cohesion: 0.22
Nodes (9): AI adoption & ROI in financial terms (BO-3), E9 AI Adoption & ROI, AI Cost Track (AI-01..AI-05), api-core application config, Copilot seat-cost assumption (api-core), staging.pull_request_state (ai_assisted flag), staging.ai_usage_state, usage.snapshot event (Claude Code / Copilot) (+1 more)

### Community 48 - "Audit Log API"
Cohesion: 0.33
Nodes (3): AuditController, AuditEntry, AuditQueryService

### Community 49 - "No-Data-Loss Ingestion"
Cohesion: 0.32
Nodes (8): FR-1.4 Resilient, idempotent ingestion pipeline, FR-1.8 No-data-loss guarantee, FRD v1.0 — AI Impact Evaluation, End-to-end smoke test (smoke-e2e.sh), Flyway migrations (staging/core/mart schemas), Dead-letter queue (staging.events.dlq), ingestion-writer, staging.raw_event (immutable)

### Community 50 - "AI ROI Metrics (AI-01..05)"
Cohesion: 0.29
Nodes (8): AI-01 Total AI Spend, AI-02 Cost per PR / Dev-Day, AI-03 Adoption Rate, AI-04 AI-assisted vs Non-AI Delta, AI-05 Dollar ROI Figure, AiCostTrackQueryService, Connector: AI Telemetry (Claude Code + Copilot), CHANGELOG

### Community 51 - "Team Snapshot Parsing"
Cohesion: 0.29
Nodes (3): java.util.regex.Pattern, org.springframework.amqp.rabbit.annotation.RabbitListener, TeamSnapshotParser

### Community 53 - "Cockpit Endpoints (E4)"
Cohesion: 0.29
Nodes (7): E4 Cockpit / Executive Dashboard, Cockpit dashboard, GET /admin/connectors endpoint, GET /metrics/ai-cost-track endpoint, GET /metrics/cockpit endpoint, api-core OpenAPI spec, mart.metric_daily

### Community 54 - "Security & Audit Standards"
Cohesion: 0.29
Nodes (7): Security & Privacy Standards, Secrets & Third-Party Credential Handling, ADR-0004 Authentication, RBAC & Audit, Append-Only Audit Log, Dev-Token Bridge, JWT Resource Server (RS256), RBAC Five Roles

### Community 55 - "Particle Field Animation"
Cohesion: 0.38
Nodes (4): makeDotTexture(), ParticleField(), animate(), renderFrame()

### Community 56 - "Vercel Deploy Config"
Cohesion: 0.29
Nodes (6): buildCommand, framework, installCommand, outputDirectory, rewrites, $schema

### Community 58 - "DORA Delivery Module (E3)"
Cohesion: 0.33
Nodes (6): DORA & Delivery module (BRD 8.2), E3 DORA & Delivery Metrics, staging.workflow_run_state, core.team_repo, DORA metrics computation, metrics-engine

### Community 59 - "Analytics & Reporting Epics"
Cohesion: 0.33
Nodes (6): E10 AI Code Review Agent, E11 Custom Reporting & Query Layer, E5 Investment Profile, E6 Code Review & PR Analytics, E7 Goals & OKR Tracking, PRD v1.0 — AI Impact Evaluation

### Community 60 - "Engineering Standards & Docs"
Cohesion: 0.40
Nodes (6): Engineering Standards, Contract-First API (OpenAPI), Testing Standards (metric tests, fixtures), Trunk-Based Development & Conventional Commits, Operations — Runbooks & Deployment, Frontend README

### Community 61 - "Identity Service & Infra"
Cohesion: 0.33
Nodes (6): Core DB (normalized entities, RBAC, audit), Identity Service, docker-compose (Postgres + RabbitMQ), PostgreSQL 16 Container, RabbitMQ 3 Container, Infra — Local Development Environment

### Community 62 - "Personal Activity (Frontend)"
Cohesion: 0.53
Nodes (5): fetchPersonalActivity(), PersonalActivity, ageBadge(), Personal(), reviewStateBadge()

### Community 63 - "Connector RabbitMQ Config"
Cohesion: 0.33
Nodes (3): Jackson2JsonMessageConverter, RabbitTemplate, RabbitConfig

### Community 64 - "Connector RabbitMQ Config"
Cohesion: 0.33
Nodes (3): Jackson2JsonMessageConverter, RabbitTemplate, RabbitConfig

### Community 65 - "Connector RabbitMQ Config"
Cohesion: 0.33
Nodes (3): Jackson2JsonMessageConverter, RabbitTemplate, RabbitConfig

### Community 66 - "Connector RabbitMQ Config"
Cohesion: 0.33
Nodes (3): Jackson2JsonMessageConverter, RabbitTemplate, RabbitConfig

### Community 67 - "Team Event Generator"
Cohesion: 0.70
Nodes (4): emit(), iso(), main(), datetime

### Community 68 - "Demo History Seeder"
Cohesion: 0.60
Nodes (3): port_healthy(), seed-demo-history.sh script, wait_healthy()

### Community 69 - "Extra Teams Seeder"
Cohesion: 0.60
Nodes (3): port_healthy(), seed-more-teams.sh script, wait_healthy()

### Community 70 - "E2E Smoke Test"
Cohesion: 0.60
Nodes (3): post_github(), smoke-e2e.sh script, wait_healthy()

### Community 71 - "Logo & Brand (Public)"
Cohesion: 0.67
Nodes (4): Purple-to-Cyan Gradient, AI Impact Evaluation Logo, Mallify Platform Brand, Serif M Monogram

### Community 72 - "Logo & Brand (Assets)"
Cohesion: 0.67
Nodes (4): Purple-to-Cyan Gradient, Mallify Logo, Elegant Serif M Monogram, Mallify AI-Powered Analytical Platform

### Community 74 - "Seed Event Generator"
Cohesion: 0.83
Nodes (3): iso(), main(), datetime

### Community 75 - "Backend Start Script"
Cohesion: 0.83
Nodes (3): start-backend.sh script, start(), wait_healthy()

### Community 76 - "Create Team Form"
Cohesion: 1.00
Nodes (3): createOrUpdateTeam(), CreateTeamForm(), handleSubmit()

## Knowledge Gaps
- **176 isolated node(s):** `name`, `private`, `version`, `type`, `dev` (+171 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **15 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `EventEnvelope` connect `Event Publisher Interfaces` to `API Security & RBAC`, `Admin REST Controllers`, `Identity Event Extraction`, `Staging Writer Integration Tests`, `Connector Backfill Services`, `Jenkins/Jira Backfill`, `Staging Event Writer`, `Jenkins Backfill Service`, `AI Telemetry Backfill`, `GitHub Event Publisher`, `Team Snapshot Parsing`, `GitHub Backfill Service`?**
  _High betweenness centrality (0.047) - this node is a cross-community bridge._
- **Why does `TeamAdminService` connect `Admin Domain Services` to `Admin REST Controllers`, `JDBC Audit & Team Repos`, `Connector Backfill Services`, `Connector Admin Controller`?**
  _High betweenness centrality (0.028) - this node is a cross-community bridge._
- **Why does `InvestmentProfileQueryService` connect `Investment Profile API` to `Admin REST Controllers`, `JDBC Audit & Team Repos`, `Connector Backfill Services`?**
  _High betweenness centrality (0.020) - this node is a cross-community bridge._
- **Are the 13 inferred relationships involving `EventEnvelope` (e.g. with `.backfill()` and `.backfill()`) actually correct?**
  _`EventEnvelope` has 13 INFERRED edges - model-reasoned connections that need verification._
- **What connects `name`, `private`, `version` to the rest of the system?**
  _176 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `API Security & RBAC` be split into smaller, more focused modules?**
  _Cohesion score 0.05405940594059406 - nodes in this community are weakly interconnected._
- **Should `Admin REST Controllers` be split into smaller, more focused modules?**
  _Cohesion score 0.051111111111111114 - nodes in this community are weakly interconnected._