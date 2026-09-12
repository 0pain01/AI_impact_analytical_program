# Graph Report - Mallify---AI-Powered-Analytical-Platform  (2026-09-12)

## Corpus Check
- 52 files · ~216,104 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1593 nodes · 3630 edges · 129 communities (91 shown, 38 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 221 edges (avg confidence: 0.82)
- Token cost: 192,070 input · 0 output

## Community Hubs (Navigation)
- Identity Resolution & Connector Event Wiring
- Staging Writer Integration Tests
- Frontend Dependencies
- Frontend API Types
- Admin API Client
- RabbitMQ Event Publishing (Connectors)
- Admin REST Controllers
- Animated Backgrounds & Motion
- Cockpit Dashboard (Frontend)
- Service Entry Points
- Webhook Token Verification
- API Security & RBAC
- Connector Admin & Auto-Refresh Services
- AI Cost Track API
- Identity Event Extraction
- Queue Config & Test Beans
- Frontend Auth & App Shell
- Code Review Analytics API
- Event Publisher Interfaces
- Connector Backfill Services
- Admin Domain Services
- Admin Panels (Teams/Users)
- TypeScript Config
- GitLab Backfill Service (Java)
- Admin REST Controllers
- AI Telemetry Backfill
- Investment Profile API
- System Architecture Concepts
- Connector Admin Controller
- API Security & RBAC
- AppUser & Dev-Token Auth
- Repo Sync Frontend
- Admin User Service
- Admin REST Controllers
- JDBC Audit & Team Repos
- Connector Health Service
- GitHub Backfill Service
- No-Data-Loss Ingestion
- Clock Config (Connectors)
- Personal Activity API
- Analytics & Reporting Epics
- README Product Concepts
- Jenkins Backfill Service
- Staging Writer Integration Tests
- Admin REST Controllers
- Admin REST Controllers
- Admin Domain Services
- Cockpit Query Service
- API Security & RBAC
- Connector Backfill Services
- Setup Status API
- Product Docs & ADRs
- GitLab Connect & Deploy-Pattern Config
- GitLab/Auto-Refresh ADRs & Changelog
- Frontend Mock Data
- JDBC Audit & Team Repos
- JDBC AppUser Repository
- Jenkins/Jira Backfill
- Identity Event Extraction
- Privacy & Product Rules
- AI Cost Track Metrics (AI-01..05)
- AI Cost Track (Frontend)
- Code Review (Frontend)
- API Security & RBAC
- Admin REST Controllers
- GitLab REST Client Internals
- Architecture Containers (C4)
- GitHub Event Publisher
- Identity Service & Infra
- API Security & RBAC
- API Security & RBAC
- JWT RSA Key Config
- Security & Audit Standards
- Logo & Brand (Public)
- Particle Field Animation
- Vercel Deploy Config
- API Security & RBAC
- JDBC Identity Repository
- Engineering Standards & Docs
- Identity Service & Infra
- GitHub/Jenkins Connector Docs
- Team Event Generator
- Demo History Seeder
- Extra Teams Seeder
- E2E Smoke Test
- AI Cost Track Data (E9)
- Identity Event Extraction
- AI Network Background
- Seed Event Generator
- Backend Start Script
- GitLab RabbitMQ Config
- Connector RabbitMQ Config
- Connector RabbitMQ Config
- Connector RabbitMQ Config
- Connector RabbitMQ Config
- DORA Change-Failure/MTTR Definitions
- DORA Lead-Time Definitions
- Backend Shutdown Script
- GitLab Event Publisher (Java)
- Jenkins Connector Docs
- Time-to-Value Metric
- PRD Cockpit Epic
- Frontend Entry Point
- Zero-Tagging Automation Principle
- Infra README
- api-core Package
- Root Maven Package
- connector-ai-telemetry Package
- connector-github Package
- GitLab Connector Package
- connector-jenkins Package
- connector-jira Package
- identity-service Package
- ingestion-writer Package
- metrics-engine Package
- platform-common Package
- Delete Team API
- Admin Connectors Health API
- Cockpit Metrics API
- Contributor Resolution (Identity)
- Templates README

## God Nodes (most connected - your core abstractions)
1. `EventEnvelope` - 48 edges
2. `StagingEventWriter` - 44 edges
3. `MetricsRecomputeServiceIntegrationTest` - 27 edges
4. `authFetch()` - 27 edges
5. `Role` - 24 edges
6. `AppUser` - 24 edges
7. `AdminUserService` - 20 edges
8. `AppUserRepository` - 19 edges
9. `ConnectorAdminService` - 19 edges
10. `IdentityEventListener` - 19 edges

## Surprising Connections (you probably didn't know these)
- `E8 Administration & Access Control` --semantically_similar_to--> `Role-Based Dashboards (5 roles)`  [INFERRED] [semantically similar]
  docs/01-product/PRD-v1.0.pdf → README.md
- `E5 Investment Profile` --semantically_similar_to--> `Investment Profile`  [INFERRED] [semantically similar]
  docs/01-product/PRD-v1.0.pdf → README.md
- `E1 Onboarding & Connectors` --implements--> `ingestion-writer`  [INFERRED]
  docs/01-product/PRD-v1.0.pdf → services/ingestion-writer/README.md
- `E9 AI Adoption & ROI` --semantically_similar_to--> `AI-01: Total AI spend`  [INFERRED] [semantically similar]
  docs/01-product/PRD-v1.0.pdf → docs/01-product/metric-definitions.md
- `E3 DORA & Delivery Metrics` --implements--> `metrics-engine`  [INFERRED]
  docs/01-product/PRD-v1.0.pdf → services/metrics-engine/README.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **GitLab Connector Containerization Flow** — readme_connector_gitlab, docs_03_architecture_decisions_adr_0005_containerize_connector_gitlab, infra_docker_compose_gitlab_service, services_connectors_connector_gitlab_readme_docker_packaging [INFERRED 0.85]
- **Scheduled Connector Health Auto-Refresh** — docs_03_architecture_decisions_adr_0006_scheduled_connector_auto_refresh, services_api_core_readme_connectorautorefreshservice, services_connectors_connector_jira_src_main_resources_application_config, services_connectors_connector_jenkins_src_main_resources_application_config [EXTRACTED 0.90]
- **AI Cost Track Impact/ROI Computation** — docs_01_product_metric_definitions_ai04, docs_01_product_metric_definitions_ai05, services_api_core_readme_aicosttrackqueryservice, services_api_core_src_main_resources_openapi_api_core_getaicosttrack [INFERRED 0.85]
- **Connector to staging ingestion pipeline** — services_connectors_connector_jira_readme_connector, services_connectors_connector_jenkins_readme_connector, services_connectors_connector_ai_telemetry_readme_connector, services_ingestion_writer_readme_ingestion_writer, services_ingestion_writer_readme_staging_raw_event [EXTRACTED 0.90]
- **Staging to Cockpit metrics flow** — services_ingestion_writer_readme_staging_raw_event, services_metrics_engine_readme_metrics_engine, services_metrics_engine_readme_mart_metric_daily [INFERRED 0.85]

## Communities (129 total, 38 thin omitted)

### Community 0 - "Identity Resolution & Connector Event Wiring"
Cohesion: 0.06
Nodes (23): com.aiimpacteval.common.events.EventEnvelope, com.aiimpacteval.identity.resolve.IdentityRepository, com.aiimpacteval.identity.resolve.IdentityResolver, com.aiimpacteval.identity.resolve.ObservedIdentity, com.fasterxml.jackson.databind.JsonNode, java.util.regex.Pattern, ObservedIdentity, org.springframework.amqp.rabbit.annotation.RabbitListener (+15 more)

### Community 1 - "Staging Writer Integration Tests"
Cohesion: 0.10
Nodes (13): java.util.concurrent.locks.ReentrantLock, org.junit.jupiter.api.BeforeAll, org.springframework.scheduling.annotation.Scheduled, org.springframework.transaction.annotation.Transactional, org.testcontainers.containers.PostgreSQLContainer, org.testcontainers.junit.jupiter.Testcontainers, JdbcTemplate, StagingEventWriterIntegrationTest (+5 more)

### Community 2 - "Frontend Dependencies"
Cohesion: 0.05
Nodes (43): autoprefixer, dependencies, gsap, lenis, lucide-react, react, react-dom, recharts (+35 more)

### Community 3 - "Frontend API Types"
Cohesion: 0.06
Nodes (38): ADR-0004, AgingPr, AgingPrsPage, AiCostAssumptions, AiCostDailySpendPoint, AiCostDeveloperAllocation, AiCostImpact, AiCostKpis (+30 more)

### Community 4 - "Admin API Client"
Cohesion: 0.10
Nodes (35): AdminUser, AuditEntry, connectGithubOrgTeams(), connectGitlabGroup(), ConnectorHealth, createAdminUser(), createOrUpdateTeam(), fetchAdminConnectors() (+27 more)

### Community 5 - "RabbitMQ Event Publishing (Connectors)"
Cohesion: 0.13
Nodes (15): org.springframework.amqp.rabbit.connection.ConnectionFactory, org.springframework.amqp.rabbit.core.RabbitTemplate, org.springframework.amqp.support.converter.Jackson2JsonMessageConverter, org.springframework.stereotype.Component, RabbitTemplate, RabbitEventPublisher, RabbitTemplate, RabbitEventPublisher (+7 more)

### Community 6 - "Admin REST Controllers"
Cohesion: 0.15
Nodes (14): org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, AdminController, AuditController, AuditEntry, CodeReviewController, InvestmentProfileController (+6 more)

### Community 7 - "Animated Backgrounds & Motion"
Cohesion: 0.11
Nodes (26): Blob, BLOBS, GradientMeshBackground(), useCanvasSize(), EASE, prefersReducedMotion(), useCountUp(), useParallax() (+18 more)

### Community 8 - "Cockpit Dashboard (Frontend)"
Cohesion: 0.11
Nodes (26): CockpitResponse, CockpitTile, fetchCockpit(), MetricKey, buildCockpitCsv(), changeFailureRateTier(), Cockpit(), CORE_DORA (+18 more)

### Community 9 - "Service Entry Points"
Cohesion: 0.10
Nodes (11): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.scheduling.annotation.EnableScheduling, ApiCoreApplication, ConnectorAiTelemetryApplication, ConnectorGithubApplication, ConnectorGitlabApplication, ConnectorJenkinsApplication, ConnectorJiraApplication (+3 more)

### Community 10 - "Webhook Token Verification"
Cohesion: 0.12
Nodes (7): org.junit.jupiter.api.Test, WebhookTokenVerifier, WebhookTokenVerifierTest, WebhookTokenVerifier, WebhookTokenVerifierTest, TeamSnapshot, GitlabGroupSnapshotParserTest

### Community 11 - "API Security & RBAC"
Cohesion: 0.12
Nodes (8): AuditSecurityTest, Stubs, SetupSecurityTest, Stubs, Stubs, TeamSecurityTest, JiraWebhookControllerTest, SimpleGrantedAuthority

### Community 12 - "Connector Admin & Auto-Refresh Services"
Cohesion: 0.16
Nodes (9): org.slf4j.Logger, org.springframework.stereotype.Service, org.springframework.web.client.RestClient, ConnectorAutoRefreshService, GithubRestClients, EventPublisher, GitlabWebhookController, IdentityResolver (+1 more)

### Community 13 - "AI Cost Track API"
Cohesion: 0.18
Nodes (13): AiCostTrackController, AiCostTrackDtos, AiCostTrackResponse, Assumptions, DailySpendPoint, DeveloperAllocation, ImpactMetrics, Kpis (+5 more)

### Community 14 - "Identity Event Extraction"
Cohesion: 0.13
Nodes (7): IdentityRepository, ObservedIdentity, Alias, Contributor, IdentityResolverTest, InMemoryRepository, Override

### Community 15 - "Queue Config & Test Beans"
Cohesion: 0.17
Nodes (8): org.springframework.amqp.core.Binding, org.springframework.amqp.core.TopicExchange, org.springframework.context.annotation.Bean, TestBeans, Jackson2JsonMessageConverter, QueueConfig, Jackson2JsonMessageConverter, QueueTopologyConfig

### Community 16 - "Frontend Auth & App Shell"
Cohesion: 0.12
Nodes (19): getSession(), login(), logout(), readSession(), Role, Session, App(), AppShell() (+11 more)

### Community 17 - "Code Review Analytics API"
Cohesion: 0.16
Nodes (12): java.sql.Array, AgingPr, AgingPrsPage, CodeReviewDtos, CodeReviewResponse, PrCycleStage, ReviewerLoad, CodeReviewQueryService (+4 more)

### Community 18 - "Event Publisher Interfaces"
Cohesion: 0.14
Nodes (8): EventPublisher, Override, RecordingEventPublisher, EventPublisher, JiraWebhookController, Override, RecordingEventPublisher, EventEnvelope

### Community 19 - "Connector Backfill Services"
Cohesion: 0.15
Nodes (10): com.fasterxml.jackson.databind.node.ArrayNode, com.fasterxml.jackson.databind.ObjectMapper, Builder, Builder, GithubTeamBackfillService, Builder, TeamBackfillResult, EventPublisher (+2 more)

### Community 20 - "Admin Domain Services"
Cohesion: 0.15
Nodes (6): org.springframework.security.oauth2.jwt.JwtEncoder, AuditEvent, AuditLog, AppUserRepository, DevTokenService, IssuedToken

### Community 21 - "Admin Panels (Teams/Users)"
Cohesion: 0.12
Nodes (16): deleteTeam(), fetchAdminUsers(), fetchInvestmentProfile(), fetchTeams(), InvestmentProfileResponse, Team, RepoTeamsPanel(), handleDeleteTeam() (+8 more)

### Community 22 - "TypeScript Config"
Cohesion: 0.09
Nodes (21): compilerOptions, allowImportingTsExtensions, isolatedModules, jsx, lib, module, moduleDetection, moduleResolution (+13 more)

### Community 23 - "GitLab Backfill Service (Java)"
Cohesion: 0.20
Nodes (6): BackfillController, BackfillResult, GitlabBackfillService, GitlabGroupBackfillService, GroupBackfillResult, EventEnvelope

### Community 24 - "Admin REST Controllers"
Cohesion: 0.28
Nodes (10): jakarta.servlet.http.HttpServletRequest, org.springframework.http.ResponseEntity, org.springframework.security.core.Authentication, org.springframework.web.bind.annotation.DeleteMapping, ConnectGithubTeamsRequest, ConnectGitlabGroupRequest, ConnectGitlabProjectRequest, ConnectorAdminController (+2 more)

### Community 25 - "AI Telemetry Backfill"
Cohesion: 0.17
Nodes (7): BackfillController, BackfillException, BackfillResult, ClaudeCodeUsageBackfillService, BackfillException, BackfillResult, CopilotUsageBackfillService

### Community 26 - "Investment Profile API"
Cohesion: 0.18
Nodes (10): CategoryCount, InvestmentProfileDtos, InvestmentProfileResponse, MonthlyBreakdown, TeamBreakdown, InvestmentProfileQueryService, CategoryCount, InvestmentProfileResponse (+2 more)

### Community 27 - "System Architecture Concepts"
Cohesion: 0.14
Nodes (19): Jenkins CI/CD Connector Implementation Brief, normalizeJenkinsResult, StagingEventWriter, staging.workflow_run_state (provider-agnostic table), Investment Profile (planned/unplanned/rework), ADR-0002 Queue-Isolated Connectors, Single Postgres, Layered Schemas (staging/core/mart), Queue-Isolated Connector Services (+11 more)

### Community 28 - "Connector Admin Controller"
Cohesion: 0.15
Nodes (10): com.aiimpacteval.apicore.audit.AuditLog, ConnectorAdminService, Builder, RepoSyncStatus, SyncState, COMPLETED, FAILED, IN_PROGRESS (+2 more)

### Community 29 - "API Security & RBAC"
Cohesion: 0.31
Nodes (9): org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.context.annotation.Import, org.springframework.security.core.authority.SimpleGrantedAuthority, org.springframework.test.context.TestPropertySource, org.springframework.test.web.servlet.MockMvc, JwtConfig, AdminConnectorSecurityTest (+1 more)

### Community 30 - "AppUser & Dev-Token Auth"
Cohesion: 0.26
Nodes (5): AppUser, DevTokenServiceTest, FakeAppUserRepository, Override, RecordingAuditLog

### Community 31 - "Repo Sync Frontend"
Cohesion: 0.22
Nodes (18): addTeamRepo(), authFetch(), connectGitlabProject(), connectRepo(), disconnectRepo(), fetchRepoSyncStatus(), listTeamRepos(), removeTeamRepo() (+10 more)

### Community 32 - "Admin User Service"
Cohesion: 0.29
Nodes (3): AdminUserService, AppUserView, NoSuchAdminUserException

### Community 33 - "Admin REST Controllers"
Cohesion: 0.15
Nodes (7): GithubBackfillService, GithubTeamBackfillService, org.springframework.web.bind.annotation.PostMapping, BackfillController, BackfillController, RecomputeController, TeamBackfillResult

### Community 34 - "JDBC Audit & Team Repos"
Cohesion: 0.15
Nodes (5): org.springframework.jdbc.core.JdbcTemplate, Builder, AuditQueryService, Override, JdbcAuditLog

### Community 35 - "Connector Health Service"
Cohesion: 0.19
Nodes (6): AdminConnectorService, ConnectorHealth, ConnectorStatus, CONNECTED, NOT_CONNECTED, STALE

### Community 36 - "GitHub Backfill Service"
Cohesion: 0.24
Nodes (6): BackfillResult, GithubBackfillService, PullRequestBackfillResult, BackfillException, RateLimitException, RetryingJsonFetcher

### Community 37 - "No-Data-Loss Ingestion"
Cohesion: 0.15
Nodes (16): DORA & Delivery module (BRD 8.2), FR-1.4 Resilient, idempotent ingestion pipeline, FR-1.8 No-data-loss guarantee, Local Development Environment, PostgreSQL 16 (aiimpacteval db), RabbitMQ 3 message broker, End-to-end smoke test (smoke-e2e.sh), Project Setup Commands (+8 more)

### Community 38 - "Clock Config (Connectors)"
Cohesion: 0.17
Nodes (6): org.springframework.context.annotation.Configuration, ClockConfig, ClockConfig, ClockConfig, ClockConfig, ClockConfig

### Community 39 - "Personal Activity API"
Cohesion: 0.20
Nodes (7): OwnPr, PersonalActivityResponse, PersonalDtos, ReviewGiven, OwnPr, ReviewGiven, PersonalQueryService

### Community 40 - "Analytics & Reporting Epics"
Cohesion: 0.23
Nodes (15): FRD v1.0 — AI Impact Evaluation, E1 Onboarding & Connectors, E10 AI Code Review Agent, E11 Custom Reporting & Query Layer, E2 Identity & Team Normalization, E3 DORA & Delivery Metrics, E4 Cockpit / Executive Dashboard, E5 Investment Profile (+7 more)

### Community 41 - "README Product Concepts"
Cohesion: 0.13
Nodes (15): Trust over surveillance (guiding principle), Admin Console, AI Cost Track (E9, AI-01..AI-05), AI Impact Evaluation Platform, API Core service, connector-ai-telemetry, connector-jira, DORA Metrics (all four, live) (+7 more)

### Community 42 - "Jenkins Backfill Service"
Cohesion: 0.21
Nodes (6): BackfillController, BackfillException, BackfillResult, Builder, JenkinsBackfillService, EventPublisher

### Community 43 - "Staging Writer Integration Tests"
Cohesion: 0.15
Nodes (3): org.junit.jupiter.api.BeforeEach, GitlabWebhookControllerTest, TestBeans

### Community 44 - "Admin REST Controllers"
Cohesion: 0.16
Nodes (6): org.springframework.boot.autoconfigure.condition.ConditionalOnProperty, org.springframework.web.bind.annotation.ExceptionHandler, DevTokenController, NoSuchAppUserException, RetryingJsonFetcher.RateLimitException, RetryingJsonFetcher.RateLimitException

### Community 45 - "Admin REST Controllers"
Cohesion: 0.22
Nodes (6): CreateTeamRequest, RepoRequest, TeamAdminController, TeamCreated, TeamAdminService.NoSuchTeamException, TeamAdminService.TeamHasDependentsException

### Community 46 - "Admin Domain Services"
Cohesion: 0.21
Nodes (3): NoSuchTeamException, TeamAdminService, TeamHasDependentsException

### Community 47 - "Cockpit Query Service"
Cohesion: 0.24
Nodes (7): CockpitDtos, CockpitResponse, CockpitTile, DailyValue, CockpitQueryService, CockpitResponse, TileSpec

### Community 49 - "Connector Backfill Services"
Cohesion: 0.27
Nodes (4): org.springframework.web.client.HttpClientErrorException, BackfillException, RateLimitException, RetryingJsonFetcher

### Community 50 - "Setup Status API"
Cohesion: 0.29
Nodes (4): SetupController, ChecklistItem, SetupQueryService, SetupStatus

### Community 51 - "Product Docs & ADRs"
Cohesion: 0.27
Nodes (11): CLAUDE.md — AI Agent Rules & Documentation Policy, Mandatory Documentation Policy, BRD Summary, Business Objectives (BO-1..BO-7), Functional Requirements Document v1.0, Product Requirements Document v1.0, Epic Map E1–E11, ADR-0000 Template (+3 more)

### Community 52 - "GitLab Connect & Deploy-Pattern Config"
Cohesion: 0.20
Nodes (11): DORA-1: Deployment frequency, GitLab deploy/hotfix detection fidelity gap, ConnectorAdminService, POST /admin/connectors/gitlab-groups (connectGitlabGroup), POST /admin/connectors/gitlab-projects (connectGitlabProject), POST /admin/connectors/repos (connectRepo), POST /internal/backfill-teams (connector-github), POST /internal/backfill (connector-gitlab) (+3 more)

### Community 53 - "GitLab/Auto-Refresh ADRs & Changelog"
Cohesion: 0.22
Nodes (11): ADR-0005: Containerize connector-gitlab, Multi-stage Docker build (Maven build stage + Alpine JRE runtime stage), Non-root container runtime user (aiimpacteval), ADR-0006: Scheduled connector auto-refresh, Two-signal connector staleness design (last checked vs last data change), ADR-0002: queue-isolated connectors, single Postgres MVP, ADR-0003: event envelope contract and queue topology, Changelog: ConnectorAutoRefreshService shipped (2026-09-12) (+3 more)

### Community 54 - "Frontend Mock Data"
Cohesion: 0.18
Nodes (10): admin, AgingPr, AuditEntry, codeReview, ConnectorStatus, investmentProfile, InvestmentSlice, InvestmentTrendPoint (+2 more)

### Community 55 - "JDBC Audit & Team Repos"
Cohesion: 0.24
Nodes (4): org.springframework.jdbc.core.RowMapper, org.springframework.stereotype.Repository, Override, JdbcTeamRepository

### Community 57 - "Jenkins/Jira Backfill"
Cohesion: 0.25
Nodes (5): BackfillException, BackfillResult, Builder, JiraBackfillService, Builder

### Community 58 - "Identity Event Extraction"
Cohesion: 0.25
Nodes (4): MemberRef, TeamSnapshot, TeamSnapshotParser, TeamSnapshotParserTest

### Community 59 - "Privacy & Product Rules"
Cohesion: 0.24
Nodes (10): Non-Negotiable Product Rules, AI adoption & ROI in financial terms (BO-3), Analytics layer only (never replaces tools), BRD Summary — AI Impact Evaluation, Least-Privilege Integrations, No Manual Tagging Dependency, No Surveillance Features (ethical exclusion), Five RBAC roles (Admin/Eng Leader/Manager/IC/Finance) (+2 more)

### Community 60 - "AI Cost Track Metrics (AI-01..05)"
Cohesion: 0.22
Nodes (10): AI-01: Total AI spend, AI-02: Cost per PR / dev-day, AI-03: Adoption rate, AI-04: AI-assisted vs non-AI delta, AI-05: Dollar ROI figure, AiCostTrackQueryService, Changelog: AI-04/AI-05 went live (2026-08-21), AiCostTrackQueryService (api-core README) (+2 more)

### Community 61 - "AI Cost Track (Frontend)"
Cohesion: 0.24
Nodes (7): AiCostTrackResponse, fetchAiCostTrack(), AiCostTrack(), currency(), Tab, TABS, TOOL_COLORS

### Community 62 - "Code Review (Frontend)"
Cohesion: 0.24
Nodes (6): CodeReviewResponse, fetchCodeReview(), ageBadge(), CodeReview(), SortBy, SortDir

### Community 63 - "API Security & RBAC"
Cohesion: 0.33
Nodes (6): JwtAuthenticationConverter, org.springframework.security.config.annotation.web.builders.HttpSecurity, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter, org.springframework.security.web.SecurityFilterChain, org.springframework.web.cors.CorsConfigurationSource, SecurityConfig

### Community 64 - "Admin REST Controllers"
Cohesion: 0.33
Nodes (6): org.springframework.web.bind.annotation.PatchMapping, AdminUserController, CreateUserRequest, SetActiveRequest, UpdateGithubLoginRequest, UpdateRoleRequest

### Community 65 - "GitLab REST Client Internals"
Cohesion: 0.20
Nodes (6): Builder, RetryingJsonFetcher, Builder, RetryingJsonFetcher, GitlabRestClients, Builder

### Community 66 - "Architecture Containers (C4)"
Cohesion: 0.22
Nodes (9): ADR-0004: authentication, RBAC, and audit enforcement in api-core, API Core (Spring Boot) container, Core DB (Postgres schema), Identity Service container, Metrics Engine container, Metrics Mart (Postgres schema), Team rollups computed from raw per-event rows, never repo-median averaging, Web App (React/TS) container (+1 more)

### Community 67 - "GitHub Event Publisher"
Cohesion: 0.22
Nodes (4): Override, Override, Override, Override

### Community 68 - "Identity Service & Infra"
Cohesion: 0.36
Nodes (8): ConnectorAutoRefreshService, docker-compose postgres service, ConnectorAutoRefreshService (api-core README), api-core connectors.* base-url config block, POST /webhooks/github, connector-github application.yml config, connector-jenkins application.yml config, connector-jira application.yml config

### Community 69 - "API Security & RBAC"
Cohesion: 0.25
Nodes (7): fromString(), Role, ADMIN, ENG_LEADER, FINANCE_READONLY, IC, MANAGER

### Community 72 - "Security & Audit Standards"
Cohesion: 0.29
Nodes (7): Security & Privacy Standards, Secrets & Third-Party Credential Handling, ADR-0004 Authentication, RBAC & Audit, Append-Only Audit Log, Dev-Token Bridge, JWT Resource Server (RS256), RBAC Five Roles

### Community 73 - "Logo & Brand (Public)"
Cohesion: 0.38
Nodes (7): Purple-to-Cyan Gradient, AI Impact Evaluation Logo, Mallify Platform Brand, Serif M Monogram, Mallify Logo, Elegant Serif M Monogram, Mallify AI-Powered Analytical Platform

### Community 74 - "Particle Field Animation"
Cohesion: 0.38
Nodes (4): makeDotTexture(), ParticleField(), animate(), renderFrame()

### Community 75 - "Vercel Deploy Config"
Cohesion: 0.29
Nodes (6): buildCommand, framework, installCommand, outputDirectory, rewrites, $schema

### Community 78 - "Engineering Standards & Docs"
Cohesion: 0.40
Nodes (6): Engineering Standards, Contract-First API (OpenAPI), Testing Standards (metric tests, fixtures), Trunk-Based Development & Conventional Commits, Operations — Runbooks & Deployment, Frontend README

### Community 79 - "Identity Service & Infra"
Cohesion: 0.33
Nodes (6): Changelog: connector-gitlab verified end-to-end (2026-09-12), docker-compose gitlab service (connector-gitlab image), docker-compose rabbitmq service, POST /webhooks/gitlab, connector-gitlab application.yml config, GitLab identity extraction email gap

### Community 80 - "GitHub/Jenkins Connector Docs"
Cohesion: 0.40
Nodes (5): Changelog: connector-github expired PAT root cause fix (2026-09-12), connector-github, connector-jenkins, staging.workflow_run_state (provider-agnostic CI/CD table), POST /internal/backfill (connector-github)

### Community 81 - "Team Event Generator"
Cohesion: 0.70
Nodes (4): emit(), iso(), main(), datetime

### Community 82 - "Demo History Seeder"
Cohesion: 0.60
Nodes (3): port_healthy(), seed-demo-history.sh script, wait_healthy()

### Community 83 - "Extra Teams Seeder"
Cohesion: 0.60
Nodes (3): port_healthy(), seed-more-teams.sh script, wait_healthy()

### Community 84 - "E2E Smoke Test"
Cohesion: 0.60
Nodes (3): post_github(), smoke-e2e.sh script, wait_healthy()

### Community 85 - "AI Cost Track Data (E9)"
Cohesion: 0.40
Nodes (5): connector-ai-telemetry, staging.ai_usage_state, usage.snapshot event (Claude Code / Copilot), connector-ai-telemetry config (usage-file seam), Copilot seat-cost assumption (ingestion-writer)

### Community 88 - "Seed Event Generator"
Cohesion: 0.83
Nodes (3): iso(), main(), datetime

### Community 89 - "Backend Start Script"
Cohesion: 0.83
Nodes (3): start-backend.sh script, start(), wait_healthy()

### Community 95 - "DORA Change-Failure/MTTR Definitions"
Cohesion: 0.67
Nodes (3): DORA-3: Change failure rate, DORA-4: Mean time to restore (MTTR), metrics-engine hotfix-workflow-pattern config

## Knowledge Gaps
- **201 isolated node(s):** `Tier`, `Stage`, `TileSpec`, `AgingPr`, `AuditEntry` (+196 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **38 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `EventEnvelope` connect `Event Publisher Interfaces` to `Identity Resolution & Connector Event Wiring`, `Staging Writer Integration Tests`, `GitHub Event Publisher`, `GitHub Backfill Service`, `RabbitMQ Event Publishing (Connectors)`, `Jenkins Backfill Service`, `Connector Admin & Auto-Refresh Services`, `Connector Backfill Services`, `AI Telemetry Backfill`, `API Security & RBAC`, `Jenkins/Jira Backfill`?**
  _High betweenness centrality (0.041) - this node is a cross-community bridge._
- **Why does `ConnectorAdminService` connect `Connector Admin Controller` to `Admin REST Controllers`, `JDBC Audit & Team Repos`, `Connector Admin & Auto-Refresh Services`?**
  _High betweenness centrality (0.020) - this node is a cross-community bridge._
- **Why does `AdminConnectorService` connect `Connector Health Service` to `JDBC Audit & Team Repos`, `Connector Admin & Auto-Refresh Services`, `Admin REST Controllers`?**
  _High betweenness centrality (0.018) - this node is a cross-community bridge._
- **What connects `Tier`, `Stage`, `TileSpec` to the rest of the system?**
  _201 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Identity Resolution & Connector Event Wiring` be split into smaller, more focused modules?**
  _Cohesion score 0.05727043292564551 - nodes in this community are weakly interconnected._
- **Should `Staging Writer Integration Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.10289115646258504 - nodes in this community are weakly interconnected._
- **Should `Frontend Dependencies` be split into smaller, more focused modules?**
  _Cohesion score 0.045454545454545456 - nodes in this community are weakly interconnected._