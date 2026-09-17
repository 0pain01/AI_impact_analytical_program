# Graph Report - Mallify---AI-Powered-Analytical-Platform  (2026-09-17)

## Corpus Check
- 30 files · ~235,328 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1766 nodes · 3910 edges · 194 communities (87 shown, 107 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 231 edges (avg confidence: 0.83)
- Token cost: 0 input · 334,487 output

## Community Hubs (Navigation)
- Identity Resolution & Connector Event Wiring
- Code Review Analytics API
- Staging Writer Integration Tests
- Frontend Dependencies
- Admin API Client
- Frontend API Types
- Admin REST Controllers
- Privacy & Product Rules
- Connector Admin Controller
- Animated Backgrounds & Motion
- Admin REST Controllers
- GitHub Backfill Service
- Cockpit Dashboard (Frontend)
- Service Entry Points
- RabbitMQ Event Publishing (Connectors)
- Identity Event Extraction
- Queue Config & Test Beans
- AI Cost Track API
- Connector Admin & Auto-Refresh Services
- GitHub Event Publisher
- Admin REST Controllers
- API Security & RBAC
- AI Telemetry Backfill
- GitLab Backfill Service (Java)
- Admin Panels (Teams/Users)
- TypeScript Config
- API Security & RBAC
- Jenkins/Jira Backfill
- Connector Containerization (ADR-0007)
- Frontend Auth & App Shell
- Admin Domain Services
- AppUser & Dev-Token Auth
- Functional Spec Modules
- Admin API Client
- Investment Profile API
- GitLab Backfill Service (Java)
- Architecture Containers (C4)
- Connector Health Service
- Admin User Service
- AI Cost Track Metrics (AI-01..05)
- Repo Sync Frontend
- RabbitMQ Event Publishing (Connectors)
- Clock Config (Connectors)
- JDBC Audit & Team Repos
- Personal Activity API
- API Security & RBAC
- Analytics & Reporting Epics
- Admin REST Controllers
- Admin REST Controllers
- No-Data-Loss Ingestion
- Jira Work Items Dashboard
- API Security & RBAC
- JDBC AppUser Repository
- API Security & RBAC
- Identity Service & Infra
- Connector Backfill Services
- Setup Status API
- Frontend Mock Data
- Admin REST Controllers
- Admin Domain Services
- Cockpit Query Service
- JDBC Audit & Team Repos
- Identity Event Extraction
- AI Cost Track (Frontend)
- Code Review (Frontend)
- JDBC Identity Repository
- Webhook Token Verification
- Staging Writer Integration Tests
- Jenkins Backfill Service
- Webhook Token Verification
- API Security & RBAC
- Frontend API Types
- API Security & RBAC
- System Architecture Concepts
- Logo & Brand (Public)
- Particle Field Animation
- Vercel Deploy Config
- Webhook Token Verification
- JWT RSA Key Config
- Frontend API Types
- Connector RabbitMQ Config
- Connector RabbitMQ Config
- Connector RabbitMQ Config
- Team Event Generator
- Demo History Seeder
- Extra Teams Seeder
- E2E Smoke Test
- AI Network Background
- Seed Event Generator
- Backend Start Script
- GitLab REST Client Internals
- Backend Shutdown Script
- GitLab Connect & Deploy-Pattern Config
- Product Docs & ADRs
- Frontend Entry Point
- PostCSS Config
- Vite Env Types
- Tailwind Config
- Vite Config
- Vite Config (Timestamped Build)
- Vite Config (Timestamped Build)
- Vite Config (Timestamped Build)
- README Product Concepts
- Zero-Tagging Automation Principle
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
- No-Data-Loss Ingestion
- GitHub/Jenkins Connector Docs
- GitLab REST Client Internals
- Contributor Resolution (Identity)
- Identity Service & Infra
- Tailwind CSS Dependency
- Templates README
- AI Attribution Detection
- Api-Core API Surface
- Core Schema
- Event Envelope Contract
- Investment Profile Algorithm
- Mart Schema
- Resilience Patterns
- Security Architecture
- Staging Schema
- Testing Strategy
- Api-Core Service
- Cloud Deployment Steps
- AI Telemetry Connector
- GitHub Connector
- Jira Connector
- Feature History
- Frontend Service
- Identity Service
- Ingestion Writer
- Metrics Engine
- Platform Common Library
- Postgres Infrastructure
- Production Hardening Gaps
- RabbitMQ Infrastructure
- BRD Design Forces
- Cockpit Call Flow
- Docker Backend Wiring
- Login Session Flow
- Frontend-Backend Mental Model
- Operations README
- Mallify Rename History
- Documentation Index
- Documentation Rules
- Trust Over Surveillance
- Zero-Tagging Automation
- Postgres Compose Service
- RabbitMQ Compose Service
- Local Infra Setup
- Api-Core Package
- Services Maven Reactor
- AI Telemetry Package
- GitHub Connector Package
- GitLab Connector Package
- Jenkins Connector Package
- Jira Connector Package
- Identity Service Package
- Ingestion Writer Package
- Metrics Engine Package
- Platform Common Package
- Project Setup Commands
- README Architecture Diagram
- README Status Summary
- README Tech Stack
- Api-Core README
- Investment Profile Endpoint
- GitHub Backfill Endpoint
- GitLab Backfill Endpoint
- GitLab Webhook Endpoint
- GitLab Retrying Fetcher
- GitLab Connector Config
- Jenkins Backfill Endpoint
- Jenkins Repo Attribution
- Contributor Identity Resolution
- GitLab Identity Gap
- Identity Service Config
- Deploy Workflow Pattern
- Hotfix Workflow Pattern

## God Nodes (most connected - your core abstractions)
1. `StagingEventWriter` - 47 edges
2. `EventEnvelope` - 46 edges
3. `authFetch()` - 36 edges
4. `ConnectorAdminService` - 29 edges
5. `MetricsRecomputeServiceIntegrationTest` - 27 edges
6. `ConnectorAdminController` - 25 edges
7. `AppUser` - 24 edges
8. `Role` - 23 edges
9. `AdminUserService` - 20 edges
10. `IdentityEventListener` - 19 edges

## Surprising Connections (you probably didn't know these)
- `FR-1.8 No-data-loss guarantee` --references--> `ingestion-writer`  [INFERRED]
  docs/01-product/FRD-v1.0.pdf → services/ingestion-writer/README.md
- `FR-1.4 Resilient, idempotent ingestion pipeline` --references--> `Idempotent staging writes (FR-1.8)`  [INFERRED]
  docs/01-product/FRD-v1.0.pdf → services/ingestion-writer/README.md
- `DORA & Delivery module (BRD 8.2)` --conceptually_related_to--> `DORA metrics computation`  [INFERRED]
  docs/01-product/BRD-Summary.pdf → services/metrics-engine/README.md
- `AI Impact Evaluation README` --references--> `CHANGELOG`  [AMBIGUOUS]
  README.md → docs/CHANGELOG.md
- `README Golden Rules` --semantically_similar_to--> `No-manual-tagging product rule`  [INFERRED] [semantically similar]
  README.md → docs/01-product/functional-specification.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Containerize-then-decontainerize decision lifecycle for connector-gitlab/connector-jenkins** — docs_03_architecture_decisions_adr_0005_containerize_connector_gitlab, docs_03_architecture_decisions_adr_0007_containerize_connector_jenkins, docs_03_architecture_decisions_adr_0008_decontainerize_connector_gitlab_and_jenkins, infra_docker_compose [INFERRED 0.85]
- **GitLab data ingestion into shared staging/DORA pipeline** — services_connectors_connector_gitlab_readme_connector_gitlab, docs_01_product_metric_definitions_dora_1, docs_03_architecture_technical_specification_staging_schema, docs_04_operations_frontend_backend_map_admin_connect_repo_flow [INFERRED 0.80]
- **Investment Profile PR<->Jira verification flow** — docs_01_product_functional_specification_investment_profile, docs_01_product_metric_definitions_investment_profile_drilldown, services_api_core_src_main_resources_openapi_api_core_investment_profile_prs_endpoint, services_api_core_src_main_resources_application_jira_site_base_url [INFERRED 0.85]
- **Scheduled Connector Health Auto-Refresh** — docs_03_architecture_decisions_adr_0006_scheduled_connector_auto_refresh, services_connectors_connector_jira_src_main_resources_application_config, services_connectors_connector_jenkins_src_main_resources_application_config [EXTRACTED 0.90]
- **Staging to Cockpit metrics flow** — services_ingestion_writer_readme_staging_raw_event, services_metrics_engine_readme_metrics_engine, services_metrics_engine_readme_mart_metric_daily [INFERRED 0.85]

## Communities (194 total, 107 thin omitted)

### Community 0 - "Identity Resolution & Connector Event Wiring"
Cohesion: 0.05
Nodes (24): com.aiimpacteval.common.events.EventEnvelope, com.aiimpacteval.identity.resolve.IdentityRepository, com.aiimpacteval.identity.resolve.IdentityResolver, com.aiimpacteval.identity.resolve.ObservedIdentity, com.fasterxml.jackson.databind.JsonNode, java.util.regex.Pattern, ObservedIdentity, org.springframework.amqp.rabbit.annotation.RabbitListener (+16 more)

### Community 1 - "Code Review Analytics API"
Cohesion: 0.05
Nodes (40): Jackson2JsonMessageConverter, org.springframework.amqp.core.Binding, org.springframework.amqp.core.TopicExchange, org.springframework.amqp.rabbit.connection.ConnectionFactory, org.springframework.amqp.rabbit.core.RabbitTemplate, org.springframework.amqp.support.converter.Jackson2JsonMessageConverter, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration (+32 more)

### Community 2 - "Staging Writer Integration Tests"
Cohesion: 0.08
Nodes (27): java.sql.Array, AgingPr, AgingPrsPage, CodeReviewDtos, CodeReviewResponse, PrCycleStage, ReviewerLoad, CodeReviewQueryService (+19 more)

### Community 3 - "Frontend Dependencies"
Cohesion: 0.10
Nodes (13): java.util.concurrent.locks.ReentrantLock, org.junit.jupiter.api.BeforeAll, org.springframework.scheduling.annotation.Scheduled, org.springframework.transaction.annotation.Transactional, org.testcontainers.containers.PostgreSQLContainer, org.testcontainers.junit.jupiter.Testcontainers, JdbcTemplate, StagingEventWriterIntegrationTest (+5 more)

### Community 4 - "Admin API Client"
Cohesion: 0.05
Nodes (42): ADR-0004, AgingPr, AgingPrsPage, AiCostAssumptions, AiCostDailySpendPoint, AiCostDeveloperAllocation, AiCostImpact, AiCostKpis (+34 more)

### Community 5 - "Frontend API Types"
Cohesion: 0.05
Nodes (43): autoprefixer, dependencies, gsap, lenis, lucide-react, react, react-dom, recharts (+35 more)

### Community 6 - "Admin REST Controllers"
Cohesion: 0.10
Nodes (15): com.aiimpacteval.apicore.security.ScopeResolver, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.RequestMapping, AdminController, AiCostTrackController, AuditController, CodeReviewController, InvestmentProfileController (+7 more)

### Community 7 - "Privacy & Product Rules"
Cohesion: 0.09
Nodes (38): project-reference Skill, Functional Specification, Delete-then-Refresh idempotency limitation (functional spec §4.8), Metric Definitions, GitLab deploy/hotfix detection fidelity gap, ADR-0005: Containerize connector-gitlab, Multi-stage Docker build decision for connector-gitlab, ADR-0007: Containerize connector-jenkins (+30 more)

### Community 8 - "Connector Admin Controller"
Cohesion: 0.10
Nodes (14): GithubBackfillService, GithubTeamBackfillService, org.springframework.boot.autoconfigure.condition.ConditionalOnProperty, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RestController, DevTokenController, IssuedToken (+6 more)

### Community 9 - "Animated Backgrounds & Motion"
Cohesion: 0.09
Nodes (28): AdminUser, AuditEntry, connectGithubOrgTeams(), connectGitlabGroup(), connectJenkinsJob(), connectJiraProject(), ConnectorHealth, createOrUpdateTeam() (+20 more)

### Community 10 - "Admin REST Controllers"
Cohesion: 0.11
Nodes (13): com.aiimpacteval.apicore.audit.AuditLog, ConnectorAdminService, Builder, JenkinsJobSyncStatus, JiraProjectSyncStatus, RepoSyncStatus, ResolvedState, SyncState (+5 more)

### Community 11 - "GitHub Backfill Service"
Cohesion: 0.10
Nodes (24): authFetch(), deleteTeam(), fetchAdminUsers(), fetchInvestmentProfile(), fetchInvestmentProfileLinkedPrs(), fetchRepos(), fetchTeams(), InvestmentProfileLinkedPrsPage (+16 more)

### Community 12 - "Cockpit Dashboard (Frontend)"
Cohesion: 0.11
Nodes (26): Blob, BLOBS, GradientMeshBackground(), useCanvasSize(), EASE, prefersReducedMotion(), useCountUp(), useParallax() (+18 more)

### Community 13 - "Service Entry Points"
Cohesion: 0.11
Nodes (26): CockpitResponse, CockpitTile, fetchCockpit(), MetricKey, buildCockpitCsv(), changeFailureRateTier(), Cockpit(), CORE_DORA (+18 more)

### Community 14 - "RabbitMQ Event Publishing (Connectors)"
Cohesion: 0.10
Nodes (11): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.scheduling.annotation.EnableScheduling, ApiCoreApplication, ConnectorAiTelemetryApplication, ConnectorGithubApplication, ConnectorGitlabApplication, ConnectorJenkinsApplication, ConnectorJiraApplication (+3 more)

### Community 15 - "Identity Event Extraction"
Cohesion: 0.18
Nodes (9): org.springframework.web.bind.annotation.PatchMapping, AdminUserController, CreateUserRequest, SetActiveRequest, UpdateGithubLoginRequest, UpdateRoleRequest, AdminUserService, AppUserView (+1 more)

### Community 16 - "Queue Config & Test Beans"
Cohesion: 0.13
Nodes (12): BackfillResult, GithubBackfillService, Builder, PullRequestBackfillResult, GithubRestClients, Builder, GithubTeamBackfillService, Builder (+4 more)

### Community 17 - "AI Cost Track API"
Cohesion: 0.15
Nodes (28): addTeamRepo(), connectGitlabProject(), disconnectJenkinsJob(), disconnectJiraProject(), disconnectRepo(), fetchJenkinsJobSyncStatus(), fetchJiraProjectSyncStatus(), fetchRepoSyncStatus() (+20 more)

### Community 18 - "Connector Admin & Auto-Refresh Services"
Cohesion: 0.13
Nodes (7): IdentityRepository, ObservedIdentity, Alias, Contributor, IdentityResolverTest, InMemoryRepository, Override

### Community 19 - "GitHub Event Publisher"
Cohesion: 0.20
Nodes (7): com.fasterxml.jackson.databind.node.ArrayNode, org.slf4j.Logger, org.springframework.stereotype.Service, org.springframework.web.client.RestClient, ConnectorAutoRefreshService, IdentityResolver, TimeoutRestClients

### Community 20 - "Admin REST Controllers"
Cohesion: 0.18
Nodes (12): AiCostTrackDtos, AiCostTrackResponse, Assumptions, DailySpendPoint, DeveloperAllocation, ImpactMetrics, Kpis, RoiMetrics (+4 more)

### Community 21 - "API Security & RBAC"
Cohesion: 0.13
Nodes (9): CreateTeamRequest, RepoRequest, TeamAdminController, TeamCreated, NoSuchTeamException, TeamAdminService, TeamHasDependentsException, TeamAdminService.NoSuchTeamException (+1 more)

### Community 22 - "AI Telemetry Backfill"
Cohesion: 0.12
Nodes (8): com.fasterxml.jackson.databind.ObjectMapper, GithubWebhookController, Builder, Builder, EventPublisher, JiraWebhookController, Builder, EventEnvelopeTest

### Community 23 - "GitLab Backfill Service (Java)"
Cohesion: 0.28
Nodes (12): jakarta.servlet.http.HttpServletRequest, org.springframework.http.ResponseEntity, org.springframework.security.core.Authentication, org.springframework.web.bind.annotation.DeleteMapping, ConnectGithubTeamsRequest, ConnectGitlabGroupRequest, ConnectGitlabProjectRequest, ConnectJenkinsJobRequest (+4 more)

### Community 24 - "Admin Panels (Teams/Users)"
Cohesion: 0.14
Nodes (5): org.springframework.security.oauth2.jwt.JwtEncoder, AuditEvent, AuditLog, AppUserRepository, DevTokenService

### Community 25 - "TypeScript Config"
Cohesion: 0.17
Nodes (12): CategoryCount, InvestmentProfileDtos, InvestmentProfileResponse, LinkedPr, LinkedPrsPage, MonthlyBreakdown, TeamBreakdown, InvestmentProfileQueryService (+4 more)

### Community 26 - "API Security & RBAC"
Cohesion: 0.15
Nodes (8): BackfillController, BackfillException, BackfillResult, ClaudeCodeUsageBackfillService, BackfillException, BackfillResult, CopilotUsageBackfillService, EventPublisher

### Community 27 - "Jenkins/Jira Backfill"
Cohesion: 0.12
Nodes (5): org.junit.jupiter.api.BeforeEach, org.springframework.test.context.TestPropertySource, GithubWebhookControllerTest, GitlabWebhookControllerTest, TestBeans

### Community 28 - "Connector Containerization (ADR-0007)"
Cohesion: 0.09
Nodes (21): compilerOptions, allowImportingTsExtensions, isolatedModules, jsx, lib, module, moduleDetection, moduleResolution (+13 more)

### Community 29 - "Frontend Auth & App Shell"
Cohesion: 0.22
Nodes (3): org.junit.jupiter.api.Test, JiraWebhookControllerTest, SimpleGrantedAuthority

### Community 30 - "Admin Domain Services"
Cohesion: 0.30
Nodes (15): org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.context.annotation.Import, org.springframework.security.core.authority.SimpleGrantedAuthority, org.springframework.test.web.servlet.MockMvc, JwtConfig, SecurityConfig, AdminConnectorSecurityTest (+7 more)

### Community 31 - "AppUser & Dev-Token Auth"
Cohesion: 0.15
Nodes (9): GitlabGroupBackfillService, GroupBackfillResult, Builder, RetryingJsonFetcher, GitlabRestClients, Builder, EventPublisher, GitlabWebhookController (+1 more)

### Community 32 - "Functional Spec Modules"
Cohesion: 0.14
Nodes (15): getSession(), logout(), readSession(), Role, Session, App(), AppShell(), initialsFor() (+7 more)

### Community 33 - "Admin API Client"
Cohesion: 0.22
Nodes (8): com.aiimpacteval.connector.gitlab.events.EventPublisher, RetryingJsonFetcher, BackfillController, BackfillResult, GitlabBackfillService, Builder, MergeRequestBackfillResult, EventEnvelope

### Community 34 - "Investment Profile API"
Cohesion: 0.26
Nodes (5): AppUser, DevTokenServiceTest, FakeAppUserRepository, Override, RecordingAuditLog

### Community 35 - "GitLab Backfill Service (Java)"
Cohesion: 0.15
Nodes (9): Override, EventPublisher, Override, Override, RecordingEventPublisher, EventPublisher, Override, Override (+1 more)

### Community 36 - "Architecture Containers (C4)"
Cohesion: 0.16
Nodes (6): org.springframework.jdbc.core.JdbcTemplate, Builder, AuditEntry, AuditQueryService, Override, JdbcAuditLog

### Community 37 - "Connector Health Service"
Cohesion: 0.19
Nodes (6): AdminConnectorService, ConnectorHealth, ConnectorStatus, CONNECTED, NOT_CONNECTED, STALE

### Community 38 - "Admin User Service"
Cohesion: 0.20
Nodes (7): OwnPr, PersonalActivityResponse, PersonalDtos, ReviewGiven, OwnPr, ReviewGiven, PersonalQueryService

### Community 39 - "AI Cost Track Metrics (AI-01..05)"
Cohesion: 0.16
Nodes (8): CockpitQueryService, com.aiimpacteval.apicore.metrics.CockpitController, com.aiimpacteval.apicore.metrics.CockpitQueryService, JwtConfig, SecurityConfig, CockpitSecurityTest, ScopeResolver, Stubs

### Community 40 - "Repo Sync Frontend"
Cohesion: 0.32
Nodes (13): FRD v1.0 — AI Impact Evaluation, Cockpit module, Product Requirements Document v1.0, E10: AI Code Review Agent, E11: Custom Reporting & Query Layer, E2: Identity & Team Normalization, E4: Cockpit / Executive Dashboard, E5: Investment Profile (+5 more)

### Community 41 - "RabbitMQ Event Publishing (Connectors)"
Cohesion: 0.27
Nodes (12): createAdminUser(), setAdminUserActive(), updateAdminUserGithubLogin(), updateAdminUserRole(), NewUserForm(), handleSubmit(), roleBadge(), roleNeedsGithub() (+4 more)

### Community 42 - "Clock Config (Connectors)"
Cohesion: 0.18
Nodes (7): fetchJiraDashboard(), JiraDashboardResponse, Jira(), SortBy, SortDir, STATUS_COLORS, statusBadge()

### Community 43 - "JDBC Audit & Team Repos"
Cohesion: 0.27
Nodes (3): org.springframework.jdbc.core.RowMapper, Override, JdbcAppUserRepository

### Community 45 - "API Security & RBAC"
Cohesion: 0.27
Nodes (4): org.springframework.web.client.HttpClientErrorException, BackfillException, RateLimitException, RetryingJsonFetcher

### Community 46 - "Analytics & Reporting Epics"
Cohesion: 0.27
Nodes (7): CockpitDtos, CockpitResponse, CockpitTile, DailyValue, CockpitQueryService, CockpitResponse, TileSpec

### Community 47 - "Admin REST Controllers"
Cohesion: 0.29
Nodes (4): SetupController, ChecklistItem, SetupQueryService, SetupStatus

### Community 48 - "Admin REST Controllers"
Cohesion: 0.18
Nodes (10): admin, AgingPr, AuditEntry, codeReview, ConnectorStatus, investmentProfile, InvestmentSlice, InvestmentTrendPoint (+2 more)

### Community 49 - "No-Data-Loss Ingestion"
Cohesion: 0.24
Nodes (10): Non-Negotiable Product Rules, AI adoption & ROI in financial terms (BO-3), Analytics layer only (never replaces tools), BRD Summary — AI Impact Evaluation, Least-Privilege Integrations, No Manual Tagging Dependency, No Surveillance Features (ethical exclusion), Five RBAC roles (Admin/Eng Leader/Manager/IC/Finance) (+2 more)

### Community 50 - "Jira Work Items Dashboard"
Cohesion: 0.24
Nodes (7): AiCostTrackResponse, fetchAiCostTrack(), AiCostTrack(), currency(), Tab, TABS, TOOL_COLORS

### Community 51 - "API Security & RBAC"
Cohesion: 0.24
Nodes (6): CodeReviewResponse, fetchCodeReview(), ageBadge(), CodeReview(), SortBy, SortDir

### Community 53 - "API Security & RBAC"
Cohesion: 0.36
Nodes (4): BackfillController, BackfillException, BackfillResult, JenkinsBackfillService

### Community 55 - "Connector Backfill Services"
Cohesion: 0.25
Nodes (9): FR-1.4 Resilient, idempotent ingestion pipeline, FR-1.8 No-data-loss guarantee, E1: Onboarding & Connectors, connector-jira, Jira webhook (shared token), Dead-letter queue (staging.events.dlq), Idempotent staging writes (FR-1.8), ingestion-writer (+1 more)

### Community 56 - "Setup Status API"
Cohesion: 0.25
Nodes (9): Investment Profile module, Jira Work Items module, No-manual-tagging product rule, No-surveillance product rule, Personal Activity module, Jira Work Items metrics definitions, 2026-09-16 Jira Work Items dashboard entry, README Golden Rules (+1 more)

### Community 57 - "Frontend Mock Data"
Cohesion: 0.31
Nodes (5): JwtAuthenticationConverter, org.springframework.security.config.annotation.web.builders.HttpSecurity, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter, org.springframework.security.web.SecurityFilterChain, org.springframework.web.cors.CorsConfigurationSource

### Community 58 - "Admin REST Controllers"
Cohesion: 0.31
Nodes (3): org.springframework.stereotype.Repository, Override, JdbcTeamRepository

### Community 59 - "Admin Domain Services"
Cohesion: 0.39
Nodes (4): BackfillController, BackfillException, BackfillResult, JiraBackfillService

### Community 60 - "Cockpit Query Service"
Cohesion: 0.33
Nodes (3): MemberRef, TeamSnapshot, TeamSnapshotParserTest

### Community 61 - "JDBC Audit & Team Repos"
Cohesion: 0.32
Nodes (6): fetchSetupStatus(), SetupChecklistItem, SetupStatus, formatMinutes(), Setup(), TimeToValuePanel()

### Community 62 - "Identity Event Extraction"
Cohesion: 0.25
Nodes (7): fromString(), Role, ADMIN, ENG_LEADER, FINANCE_READONLY, IC, MANAGER

### Community 63 - "AI Cost Track (Frontend)"
Cohesion: 0.33
Nodes (7): CLAUDE.md — AI Agent Rules & Documentation Policy, Mandatory Documentation Policy, BRD Summary, Functional Requirements Document v1.0, ADR-0000 Template, ADR-0001 Core Technology Stack, Core Technology Stack

### Community 64 - "Code Review (Frontend)"
Cohesion: 0.29
Nodes (7): Security & Privacy Standards, Secrets & Third-Party Credential Handling, ADR-0004 Authentication, RBAC & Audit, Append-Only Audit Log, Dev-Token Bridge, JWT Resource Server (RS256), RBAC Five Roles

### Community 65 - "JDBC Identity Repository"
Cohesion: 0.29
Nodes (7): ADR-0002 Queue-Isolated Connectors, Single Postgres, Layered Schemas (staging/core/mart), Queue-Isolated Connector Services, ADR-0003 Event Envelope & Queue Topology, Event Envelope Contract, Idempotency Key (source, sourceId, eventType), RabbitMQ Topology (aiimpacteval.events)

### Community 66 - "Webhook Token Verification"
Cohesion: 0.38
Nodes (7): Purple-to-Cyan Gradient, AI Impact Evaluation Logo, Mallify Platform Brand, Serif M Monogram, Mallify Logo, Elegant Serif M Monogram, Mallify AI-Powered Analytical Platform

### Community 67 - "Staging Writer Integration Tests"
Cohesion: 0.38
Nodes (4): makeDotTexture(), ParticleField(), animate(), renderFrame()

### Community 68 - "Jenkins Backfill Service"
Cohesion: 0.29
Nodes (6): buildCommand, framework, installCommand, outputDirectory, rewrites, $schema

### Community 72 - "API Security & RBAC"
Cohesion: 0.33
Nodes (6): ConnectorAutoRefreshService, ADR-0006: Scheduled connector auto-refresh, Two-signal connector staleness design (last checked vs last data change), ADR-0006: Scheduled connector auto-refresh, connector-jenkins application.yml config, connector-jira application.yml config

### Community 73 - "System Architecture Concepts"
Cohesion: 0.53
Nodes (5): fetchPersonalActivity(), PersonalActivity, ageBadge(), Personal(), reviewStateBadge()

### Community 74 - "Logo & Brand (Public)"
Cohesion: 0.33
Nodes (3): Override, RecordingEventPublisher, TestBeans

### Community 75 - "Particle Field Animation"
Cohesion: 0.40
Nodes (5): DORA & Delivery module (BRD 8.2), E3: DORA & Delivery Metrics, DORA metrics computation, mart.metric_daily, metrics-engine

### Community 76 - "Vercel Deploy Config"
Cohesion: 0.40
Nodes (5): AI-01: Total AI spend, AI-04: AI-assisted vs. non-AI delta, AI-05: Dollar ROI figure, ai-cost.* assumptions config block, GET /metrics/ai-cost-track

### Community 77 - "Webhook Token Verification"
Cohesion: 0.50
Nodes (5): Engineering Standards, Contract-First API (OpenAPI), Testing Standards (metric tests, fixtures), Trunk-Based Development & Conventional Commits, Frontend README

### Community 78 - "JWT RSA Key Config"
Cohesion: 0.70
Nodes (4): emit(), iso(), main(), datetime

### Community 79 - "Frontend API Types"
Cohesion: 0.60
Nodes (3): port_healthy(), seed-demo-history.sh script, wait_healthy()

### Community 80 - "Connector RabbitMQ Config"
Cohesion: 0.60
Nodes (3): port_healthy(), seed-more-teams.sh script, wait_healthy()

### Community 81 - "Connector RabbitMQ Config"
Cohesion: 0.60
Nodes (3): post_github(), smoke-e2e.sh script, wait_healthy()

### Community 82 - "Connector RabbitMQ Config"
Cohesion: 0.40
Nodes (5): connector-ai-telemetry, staging.ai_usage_state, usage.snapshot event (Claude Code / Copilot), connector-ai-telemetry config (usage-file seam), Copilot seat-cost assumption (ingestion-writer)

### Community 84 - "Demo History Seeder"
Cohesion: 0.50
Nodes (4): Investment Profile PR<->Jira verification drill-down, 2026-09-17 Investment Profile PR<->Jira drill-down entry, jira.site-base-url config, GET /metrics/investment-profile/prs

### Community 86 - "E2E Smoke Test"
Cohesion: 0.83
Nodes (3): iso(), main(), datetime

### Community 87 - "AI Network Background"
Cohesion: 0.83
Nodes (3): start-backend.sh script, start(), wait_healthy()

### Community 88 - "Seed Event Generator"
Cohesion: 1.00
Nodes (3): AWS Deployment Plan, AWS Tier 1: Single EC2 instance, AWS Tier 2: Managed services (RDS/Amazon MQ/ECS Fargate)

### Community 89 - "Backend Start Script"
Cohesion: 1.00
Nodes (3): connectRepo(), ConnectRepoForm(), handleSubmit()

## Ambiguous Edges - Review These
- `AI Impact Evaluation README` → `CHANGELOG`  [AMBIGUOUS]
  README.md · relation: references

## Knowledge Gaps
- **266 isolated node(s):** `Tier`, `SortBy`, `SortDir`, `AgingPr`, `AuditEntry` (+261 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **107 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `AI Impact Evaluation README` and `CHANGELOG`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **Why does `EventEnvelope` connect `GitLab Backfill Service (Java)` to `Identity Resolution & Connector Event Wiring`, `Code Review Analytics API`, `Connector Admin Controller`, `Admin Domain Services`, `Logo & Brand (Public)`, `Queue Config & Test Beans`, `GitHub Event Publisher`, `API Security & RBAC`, `AI Telemetry Backfill`, `API Security & RBAC`, `Jenkins/Jira Backfill`?**
  _High betweenness centrality (0.029) - this node is a cross-community bridge._
- **Why does `AiCostTrackQueryService` connect `Admin REST Controllers` to `GitHub Event Publisher`, `Architecture Containers (C4)`, `Admin REST Controllers`?**
  _High betweenness centrality (0.015) - this node is a cross-community bridge._
- **Why does `StagingEventWriter` connect `Identity Resolution & Connector Event Wiring` to `Code Review Analytics API`, `Frontend Dependencies`, `Architecture Containers (C4)`, `GitHub Event Publisher`, `AI Telemetry Backfill`, `Jenkins/Jira Backfill`?**
  _High betweenness centrality (0.014) - this node is a cross-community bridge._
- **What connects `Tier`, `SortBy`, `SortDir` to the rest of the system?**
  _266 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Identity Resolution & Connector Event Wiring` be split into smaller, more focused modules?**
  _Cohesion score 0.05448774466584377 - nodes in this community are weakly interconnected._
- **Should `Code Review Analytics API` be split into smaller, more focused modules?**
  _Cohesion score 0.0502814987381091 - nodes in this community are weakly interconnected._