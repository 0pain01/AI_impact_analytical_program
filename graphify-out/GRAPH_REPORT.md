# Graph Report - Mallify---AI-Powered-Analytical-Platform  (2026-09-17)

## Corpus Check
- 38 files · ~237,397 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1697 nodes · 3952 edges · 122 communities (92 shown, 30 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 235 edges (avg confidence: 0.83)
- Token cost: 0 input · 212,716 output

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
- Templates README

## God Nodes (most connected - your core abstractions)
1. `StagingEventWriter` - 47 edges
2. `EventEnvelope` - 46 edges
3. `authFetch()` - 35 edges
4. `ConnectorAdminService` - 29 edges
5. `MetricsRecomputeServiceIntegrationTest` - 27 edges
6. `ConnectorAdminController` - 25 edges
7. `System Architecture` - 25 edges
8. `AppUser` - 24 edges
9. `Role` - 23 edges
10. `Metric Definitions` - 21 edges

## Surprising Connections (you probably didn't know these)
- `FR-1.8 No-data-loss guarantee` --references--> `ingestion-writer`  [INFERRED]
  docs/01-product/FRD-v1.0.pdf → services/ingestion-writer/README.md
- `DORA & Delivery module (BRD 8.2)` --conceptually_related_to--> `DORA metrics computation`  [INFERRED]
  docs/01-product/BRD-Summary.pdf → services/metrics-engine/README.md
- `FR-1.4 Resilient, idempotent ingestion pipeline` --references--> `Idempotent staging writes (FR-1.8)`  [INFERRED]
  docs/01-product/FRD-v1.0.pdf → services/ingestion-writer/README.md
- `E1: Onboarding & Connectors` --implements--> `ingestion-writer`  [INFERRED]
  docs/01-product/prd.md → services/ingestion-writer/README.md
- `E3: DORA & Delivery Metrics` --implements--> `metrics-engine`  [INFERRED]
  docs/01-product/prd.md → services/metrics-engine/README.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Ingestion pipeline: connectors -> queue -> ingestion-writer** — docs_03_architecture_system_architecture_connectorgithub, docs_03_architecture_system_architecture_connectorgitlab, docs_03_architecture_system_architecture_connectorjira, docs_03_architecture_system_architecture_connectorjenkins, docs_03_architecture_system_architecture_connectoraitelemetry, docs_03_architecture_system_architecture_messagequeue, docs_03_architecture_system_architecture_ingestionwriter [EXTRACTED 1.00]
- **Connector containerization decision lineage** — docs_03_architecture_system_architecture_adr0002, docs_03_architecture_system_architecture_adr0005, docs_03_architecture_decisions_adr_0007_containerize_connector_jenkins_adr0007 [EXTRACTED 1.00]
- **DORA metric suite (deployment frequency, lead time, CFR, MTTR)** — docs_01_product_metric_definitions_dora1, docs_01_product_metric_definitions_dora2, docs_01_product_metric_definitions_dora2b, docs_01_product_metric_definitions_dora3, docs_01_product_metric_definitions_dora4 [EXTRACTED 1.00]
- **Scheduled Connector Health Auto-Refresh** — docs_03_architecture_decisions_adr_0006_scheduled_connector_auto_refresh, services_connectors_connector_jira_src_main_resources_application_config, services_connectors_connector_jenkins_src_main_resources_application_config [EXTRACTED 0.90]
- **GitLab Connector Containerization Flow** — docs_03_architecture_decisions_adr_0005_containerize_connector_gitlab [INFERRED 0.85]
- **Staging to Cockpit metrics flow** — services_ingestion_writer_readme_staging_raw_event, services_metrics_engine_readme_metrics_engine, services_metrics_engine_readme_mart_metric_daily [INFERRED 0.85]

## Communities (122 total, 30 thin omitted)

### Community 0 - "Identity Resolution & Connector Event Wiring"
Cohesion: 0.06
Nodes (22): com.aiimpacteval.common.events.EventEnvelope, com.aiimpacteval.identity.resolve.IdentityRepository, com.aiimpacteval.identity.resolve.IdentityResolver, com.aiimpacteval.identity.resolve.ObservedIdentity, com.fasterxml.jackson.databind.JsonNode, java.util.regex.Pattern, ObservedIdentity, org.springframework.amqp.rabbit.annotation.RabbitListener (+14 more)

### Community 1 - "Code Review Analytics API"
Cohesion: 0.08
Nodes (27): java.sql.Array, AgingPr, AgingPrsPage, CodeReviewDtos, CodeReviewResponse, PrCycleStage, ReviewerLoad, CodeReviewQueryService (+19 more)

### Community 2 - "Staging Writer Integration Tests"
Cohesion: 0.10
Nodes (13): java.util.concurrent.locks.ReentrantLock, org.junit.jupiter.api.BeforeAll, org.springframework.scheduling.annotation.Scheduled, org.springframework.transaction.annotation.Transactional, org.testcontainers.containers.PostgreSQLContainer, org.testcontainers.junit.jupiter.Testcontainers, JdbcTemplate, StagingEventWriterIntegrationTest (+5 more)

### Community 3 - "Frontend Dependencies"
Cohesion: 0.05
Nodes (43): autoprefixer, dependencies, gsap, lenis, lucide-react, react, react-dom, recharts (+35 more)

### Community 4 - "Admin API Client"
Cohesion: 0.09
Nodes (39): AdminUser, AuditEntry, authFetch(), connectGithubOrgTeams(), connectGitlabGroup(), ConnectorHealth, createAdminUser(), createOrUpdateTeam() (+31 more)

### Community 5 - "Frontend API Types"
Cohesion: 0.05
Nodes (40): ADR-0004, AgingPr, AgingPrsPage, AiCostAssumptions, AiCostDailySpendPoint, AiCostDeveloperAllocation, AiCostImpact, AiCostKpis (+32 more)

### Community 6 - "Admin REST Controllers"
Cohesion: 0.11
Nodes (14): GithubBackfillService, GithubTeamBackfillService, org.springframework.boot.autoconfigure.condition.ConditionalOnProperty, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RestController, DevTokenController, IssuedToken (+6 more)

### Community 7 - "Privacy & Product Rules"
Cohesion: 0.08
Nodes (31): CLAUDE.md — AI Agent Rules & Documentation Policy, Mandatory Documentation Policy, Non-Negotiable Product Rules, BRD Summary, AI adoption & ROI in financial terms (BO-3), Analytics layer only (never replaces tools), BRD Summary — AI Impact Evaluation, DORA & Delivery module (BRD 8.2) (+23 more)

### Community 8 - "Connector Admin Controller"
Cohesion: 0.11
Nodes (13): com.aiimpacteval.apicore.audit.AuditLog, ConnectorAdminService, Builder, JenkinsJobSyncStatus, JiraProjectSyncStatus, RepoSyncStatus, ResolvedState, SyncState (+5 more)

### Community 9 - "Animated Backgrounds & Motion"
Cohesion: 0.11
Nodes (26): Blob, BLOBS, GradientMeshBackground(), useCanvasSize(), EASE, prefersReducedMotion(), useCountUp(), useParallax() (+18 more)

### Community 10 - "Admin REST Controllers"
Cohesion: 0.14
Nodes (10): org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.RequestMapping, AuditController, AuditEntry, CodeReviewController, InvestmentProfileController, JiraDashboardController, CockpitController (+2 more)

### Community 11 - "GitHub Backfill Service"
Cohesion: 0.12
Nodes (12): BackfillResult, GithubBackfillService, Builder, PullRequestBackfillResult, GithubRestClients, Builder, GithubTeamBackfillService, Builder (+4 more)

### Community 12 - "Cockpit Dashboard (Frontend)"
Cohesion: 0.11
Nodes (26): CockpitResponse, CockpitTile, fetchCockpit(), MetricKey, buildCockpitCsv(), changeFailureRateTier(), Cockpit(), CORE_DORA (+18 more)

### Community 13 - "Service Entry Points"
Cohesion: 0.10
Nodes (11): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.scheduling.annotation.EnableScheduling, ApiCoreApplication, ConnectorAiTelemetryApplication, ConnectorGithubApplication, ConnectorGitlabApplication, ConnectorJenkinsApplication, ConnectorJiraApplication (+3 more)

### Community 14 - "RabbitMQ Event Publishing (Connectors)"
Cohesion: 0.17
Nodes (10): org.springframework.amqp.rabbit.connection.ConnectionFactory, org.springframework.amqp.rabbit.core.RabbitTemplate, org.springframework.stereotype.Component, RabbitEventPublisher, RabbitEventPublisher, Override, RabbitEventPublisher, RabbitEventPublisher (+2 more)

### Community 15 - "Identity Event Extraction"
Cohesion: 0.13
Nodes (8): IdentityRepository, IdentityResolver, ObservedIdentity, Alias, Contributor, IdentityResolverTest, InMemoryRepository, Override

### Community 16 - "Queue Config & Test Beans"
Cohesion: 0.16
Nodes (8): org.springframework.amqp.core.Binding, org.springframework.amqp.core.TopicExchange, org.springframework.context.annotation.Bean, TestBeans, TestBeans, Jackson2JsonMessageConverter, QueueConfig, QueueTopologyConfig

### Community 17 - "AI Cost Track API"
Cohesion: 0.18
Nodes (13): AiCostTrackController, AiCostTrackDtos, AiCostTrackResponse, Assumptions, DailySpendPoint, DeveloperAllocation, ImpactMetrics, Kpis (+5 more)

### Community 18 - "Connector Admin & Auto-Refresh Services"
Cohesion: 0.20
Nodes (6): com.fasterxml.jackson.databind.node.ArrayNode, org.slf4j.Logger, org.springframework.stereotype.Service, org.springframework.web.client.RestClient, ConnectorAutoRefreshService, TimeoutRestClients

### Community 19 - "GitHub Event Publisher"
Cohesion: 0.12
Nodes (12): Override, EventPublisher, Override, GithubWebhookController, Override, RecordingEventPublisher, EventPublisher, Override (+4 more)

### Community 20 - "Admin REST Controllers"
Cohesion: 0.28
Nodes (12): jakarta.servlet.http.HttpServletRequest, org.springframework.http.ResponseEntity, org.springframework.security.core.Authentication, org.springframework.web.bind.annotation.DeleteMapping, ConnectGithubTeamsRequest, ConnectGitlabGroupRequest, ConnectGitlabProjectRequest, ConnectJenkinsJobRequest (+4 more)

### Community 21 - "API Security & RBAC"
Cohesion: 0.20
Nodes (4): org.junit.jupiter.api.Test, SetupSecurityTest, Stubs, SimpleGrantedAuthority

### Community 22 - "AI Telemetry Backfill"
Cohesion: 0.15
Nodes (8): BackfillController, BackfillException, BackfillResult, ClaudeCodeUsageBackfillService, BackfillException, BackfillResult, CopilotUsageBackfillService, EventPublisher

### Community 23 - "GitLab Backfill Service (Java)"
Cohesion: 0.15
Nodes (9): com.fasterxml.jackson.databind.ObjectMapper, GitlabGroupBackfillService, GroupBackfillResult, Builder, RetryingJsonFetcher, EventPublisher, GitlabWebhookController, EventEnvelope (+1 more)

### Community 24 - "Admin Panels (Teams/Users)"
Cohesion: 0.13
Nodes (18): deleteTeam(), fetchInvestmentProfile(), fetchRepos(), fetchTeams(), InvestmentProfileResponse, Team, RepoTeamsPanel(), handleDeleteTeam() (+10 more)

### Community 25 - "TypeScript Config"
Cohesion: 0.09
Nodes (21): compilerOptions, allowImportingTsExtensions, isolatedModules, jsx, lib, module, moduleDetection, moduleResolution (+13 more)

### Community 26 - "API Security & RBAC"
Cohesion: 0.29
Nodes (13): org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.context.annotation.Import, org.springframework.security.core.authority.SimpleGrantedAuthority, org.springframework.test.web.servlet.MockMvc, JwtConfig, SecurityConfig, AdminConnectorSecurityTest (+5 more)

### Community 27 - "Jenkins/Jira Backfill"
Cohesion: 0.13
Nodes (9): Builder, BackfillController, BackfillException, BackfillResult, Builder, JiraBackfillService, EventPublisher, JiraWebhookController (+1 more)

### Community 28 - "Connector Containerization (ADR-0007)"
Cohesion: 0.18
Nodes (20): ADR-0007: Containerize connector-jenkins, ADR-0007 Document, ADR-0002: Queue-isolated connectors, single Postgres MVP, ADR-0003: Event envelope contract and queue topology, ADR-0005: Containerize connector-gitlab, Connector: GitLab (+ CI/CD), Connector: Jenkins, Deployment Guide (+12 more)

### Community 29 - "Frontend Auth & App Shell"
Cohesion: 0.14
Nodes (16): getSession(), logout(), readSession(), Role, Session, App(), AppShell(), initialsFor() (+8 more)

### Community 30 - "Admin Domain Services"
Cohesion: 0.17
Nodes (5): org.springframework.security.oauth2.jwt.JwtEncoder, AuditEvent, AuditLog, AppUserRepository, DevTokenService

### Community 31 - "AppUser & Dev-Token Auth"
Cohesion: 0.23
Nodes (5): AppUser, DevTokenServiceTest, FakeAppUserRepository, Override, RecordingAuditLog

### Community 32 - "Functional Spec Modules"
Cohesion: 0.15
Nodes (18): Admin Console module, AI Cost Track module, Cockpit module (DORA), Code Review Analytics module, Functional Specification, Investment Profile module, Jira Work Items module, Personal Activity module (+10 more)

### Community 33 - "Admin API Client"
Cohesion: 0.18
Nodes (20): connectJenkinsJob(), connectJiraProject(), disconnectJenkinsJob(), disconnectJiraProject(), fetchJenkinsJobSyncStatus(), fetchJiraProjectSyncStatus(), ConnectJenkinsJobForm(), handleSubmit() (+12 more)

### Community 34 - "Investment Profile API"
Cohesion: 0.18
Nodes (10): CategoryCount, InvestmentProfileDtos, InvestmentProfileResponse, MonthlyBreakdown, TeamBreakdown, InvestmentProfileQueryService, CategoryCount, InvestmentProfileResponse (+2 more)

### Community 35 - "GitLab Backfill Service (Java)"
Cohesion: 0.22
Nodes (8): com.aiimpacteval.connector.gitlab.events.EventPublisher, RetryingJsonFetcher, BackfillController, BackfillResult, GitlabBackfillService, Builder, MergeRequestBackfillResult, EventEnvelope

### Community 36 - "Architecture Containers (C4)"
Cohesion: 0.11
Nodes (19): AI-03: Adoption rate, ADR-0001: Technology stack, ADR-0004: Auth, RBAC and audit enforcement in api-core, ADR-0006: Scheduled connector auto-refresh, API Core (Spring Boot), Connector: AI Telemetry, Connector: GitHub (+ Actions), Connector: Jira (+11 more)

### Community 37 - "Connector Health Service"
Cohesion: 0.19
Nodes (7): AdminConnectorService, ConnectorHealth, ConnectorStatus, CONNECTED, NOT_CONNECTED, STALE, AdminController

### Community 38 - "Admin User Service"
Cohesion: 0.29
Nodes (3): AdminUserService, AppUserView, NoSuchAdminUserException

### Community 39 - "AI Cost Track Metrics (AI-01..05)"
Cohesion: 0.16
Nodes (16): AI-01: Total AI spend, AI-02: Cost per PR / dev-day, AI-04: AI-assisted vs non-AI delta, AI-05: Dollar ROI figure, Metric Definitions, DORA-1: Deployment frequency, DORA-2: Lead time for changes, DORA-2b: Ticket lead time (+8 more)

### Community 40 - "Repo Sync Frontend"
Cohesion: 0.19
Nodes (16): addTeamRepo(), connectGitlabProject(), connectRepo(), disconnectRepo(), fetchRepoSyncStatus(), removeTeamRepo(), ConnectGitlabProjectForm(), handleSubmit() (+8 more)

### Community 41 - "RabbitMQ Event Publishing (Connectors)"
Cohesion: 0.15
Nodes (8): Jackson2JsonMessageConverter, org.springframework.amqp.support.converter.Jackson2JsonMessageConverter, Jackson2JsonMessageConverter, RabbitTemplate, RabbitConfig, RabbitTemplate, RabbitConfig, Jackson2JsonMessageConverter

### Community 42 - "Clock Config (Connectors)"
Cohesion: 0.17
Nodes (6): org.springframework.context.annotation.Configuration, ClockConfig, ClockConfig, ClockConfig, ClockConfig, ClockConfig

### Community 43 - "JDBC Audit & Team Repos"
Cohesion: 0.16
Nodes (5): org.springframework.jdbc.core.JdbcTemplate, Builder, AuditQueryService, Override, JdbcAuditLog

### Community 44 - "Personal Activity API"
Cohesion: 0.20
Nodes (7): OwnPr, PersonalActivityResponse, PersonalDtos, ReviewGiven, OwnPr, ReviewGiven, PersonalQueryService

### Community 45 - "API Security & RBAC"
Cohesion: 0.16
Nodes (8): CockpitQueryService, com.aiimpacteval.apicore.metrics.CockpitController, com.aiimpacteval.apicore.metrics.CockpitQueryService, JwtConfig, SecurityConfig, CockpitSecurityTest, ScopeResolver, Stubs

### Community 46 - "Analytics & Reporting Epics"
Cohesion: 0.30
Nodes (15): FRD v1.0 — AI Impact Evaluation, Product Requirements Document v1.0, E1: Onboarding & Connectors, E10: AI Code Review Agent, E11: Custom Reporting & Query Layer, E2: Identity & Team Normalization, E4: Cockpit / Executive Dashboard, E5: Investment Profile (+7 more)

### Community 47 - "Admin REST Controllers"
Cohesion: 0.18
Nodes (6): CreateTeamRequest, RepoRequest, TeamAdminController, TeamCreated, TeamAdminService.NoSuchTeamException, TeamAdminService.TeamHasDependentsException

### Community 48 - "Admin REST Controllers"
Cohesion: 0.26
Nodes (4): com.aiimpacteval.apicore.security.ScopeResolver, TeamController, TeamQueryService, TeamSummary

### Community 49 - "No-Data-Loss Ingestion"
Cohesion: 0.17
Nodes (13): FR-1.4 Resilient, idempotent ingestion pipeline, FR-1.8 No-data-loss guarantee, connector-ai-telemetry, staging.ai_usage_state, usage.snapshot event (Claude Code / Copilot), connector-ai-telemetry config (usage-file seam), Dead-letter queue (staging.events.dlq), Idempotent staging writes (FR-1.8) (+5 more)

### Community 50 - "Jira Work Items Dashboard"
Cohesion: 0.18
Nodes (7): fetchJiraDashboard(), JiraDashboardResponse, Jira(), SortBy, SortDir, STATUS_COLORS, statusBadge()

### Community 51 - "API Security & RBAC"
Cohesion: 0.22
Nodes (4): org.junit.jupiter.api.BeforeEach, org.springframework.test.context.TestPropertySource, GithubWebhookControllerTest, JiraWebhookControllerTest

### Community 52 - "JDBC AppUser Repository"
Cohesion: 0.27
Nodes (3): org.springframework.jdbc.core.RowMapper, Override, JdbcAppUserRepository

### Community 54 - "Identity Service & Infra"
Cohesion: 0.18
Nodes (12): ADR-0005: Containerize connector-gitlab, Multi-stage Docker build (Maven build stage + Alpine JRE runtime stage), Non-root container runtime user (aiimpacteval), ConnectorAutoRefreshService, ADR-0006: Scheduled connector auto-refresh, Two-signal connector staleness design (last checked vs last data change), api-core connectors.* base-url config block, POST /webhooks/github (+4 more)

### Community 55 - "Connector Backfill Services"
Cohesion: 0.27
Nodes (4): org.springframework.web.client.HttpClientErrorException, BackfillException, RateLimitException, RetryingJsonFetcher

### Community 56 - "Setup Status API"
Cohesion: 0.29
Nodes (4): SetupController, ChecklistItem, SetupQueryService, SetupStatus

### Community 57 - "Frontend Mock Data"
Cohesion: 0.18
Nodes (10): admin, AgingPr, AuditEntry, codeReview, ConnectorStatus, investmentProfile, InvestmentSlice, InvestmentTrendPoint (+2 more)

### Community 58 - "Admin REST Controllers"
Cohesion: 0.29
Nodes (6): org.springframework.web.bind.annotation.PatchMapping, AdminUserController, CreateUserRequest, SetActiveRequest, UpdateGithubLoginRequest, UpdateRoleRequest

### Community 59 - "Admin Domain Services"
Cohesion: 0.25
Nodes (3): NoSuchTeamException, TeamAdminService, TeamHasDependentsException

### Community 60 - "Cockpit Query Service"
Cohesion: 0.31
Nodes (7): CockpitDtos, CockpitResponse, CockpitTile, DailyValue, CockpitQueryService, CockpitResponse, TileSpec

### Community 61 - "JDBC Audit & Team Repos"
Cohesion: 0.22
Nodes (3): Override, JdbcTeamRepository, TeamRepository

### Community 62 - "Identity Event Extraction"
Cohesion: 0.25
Nodes (4): MemberRef, TeamSnapshot, TeamSnapshotParser, TeamSnapshotParserTest

### Community 63 - "AI Cost Track (Frontend)"
Cohesion: 0.24
Nodes (7): AiCostTrackResponse, fetchAiCostTrack(), AiCostTrack(), currency(), Tab, TABS, TOOL_COLORS

### Community 64 - "Code Review (Frontend)"
Cohesion: 0.24
Nodes (6): CodeReviewResponse, fetchCodeReview(), ageBadge(), CodeReview(), SortBy, SortDir

### Community 65 - "JDBC Identity Repository"
Cohesion: 0.29
Nodes (3): org.springframework.stereotype.Repository, Override, JdbcIdentityRepository

### Community 67 - "Staging Writer Integration Tests"
Cohesion: 0.24
Nodes (3): GitlabWebhookControllerTest, RecordingEventPublisher, TestBeans

### Community 68 - "Jenkins Backfill Service"
Cohesion: 0.36
Nodes (4): BackfillController, BackfillException, BackfillResult, JenkinsBackfillService

### Community 70 - "API Security & RBAC"
Cohesion: 0.31
Nodes (5): JwtAuthenticationConverter, org.springframework.security.config.annotation.web.builders.HttpSecurity, org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter, org.springframework.security.web.SecurityFilterChain, org.springframework.web.cors.CorsConfigurationSource

### Community 71 - "Frontend API Types"
Cohesion: 0.32
Nodes (6): fetchSetupStatus(), SetupChecklistItem, SetupStatus, formatMinutes(), Setup(), TimeToValuePanel()

### Community 72 - "API Security & RBAC"
Cohesion: 0.25
Nodes (7): fromString(), Role, ADMIN, ENG_LEADER, FINANCE_READONLY, IC, MANAGER

### Community 73 - "System Architecture Concepts"
Cohesion: 0.29
Nodes (7): ADR-0002 Queue-Isolated Connectors, Single Postgres, Layered Schemas (staging/core/mart), Queue-Isolated Connector Services, ADR-0003 Event Envelope & Queue Topology, Event Envelope Contract, Idempotency Key (source, sourceId, eventType), RabbitMQ Topology (aiimpacteval.events)

### Community 74 - "Logo & Brand (Public)"
Cohesion: 0.38
Nodes (7): Purple-to-Cyan Gradient, AI Impact Evaluation Logo, Mallify Platform Brand, Serif M Monogram, Mallify Logo, Elegant Serif M Monogram, Mallify AI-Powered Analytical Platform

### Community 75 - "Particle Field Animation"
Cohesion: 0.38
Nodes (4): makeDotTexture(), ParticleField(), animate(), renderFrame()

### Community 76 - "Vercel Deploy Config"
Cohesion: 0.29
Nodes (6): buildCommand, framework, installCommand, outputDirectory, rewrites, $schema

### Community 79 - "Frontend API Types"
Cohesion: 0.53
Nodes (5): fetchPersonalActivity(), PersonalActivity, ageBadge(), Personal(), reviewStateBadge()

### Community 80 - "Connector RabbitMQ Config"
Cohesion: 0.33
Nodes (3): Jackson2JsonMessageConverter, RabbitTemplate, RabbitConfig

### Community 81 - "Connector RabbitMQ Config"
Cohesion: 0.33
Nodes (3): Jackson2JsonMessageConverter, RabbitTemplate, RabbitConfig

### Community 82 - "Connector RabbitMQ Config"
Cohesion: 0.33
Nodes (3): Jackson2JsonMessageConverter, RabbitTemplate, RabbitConfig

### Community 83 - "Team Event Generator"
Cohesion: 0.70
Nodes (4): emit(), iso(), main(), datetime

### Community 84 - "Demo History Seeder"
Cohesion: 0.60
Nodes (3): port_healthy(), seed-demo-history.sh script, wait_healthy()

### Community 85 - "Extra Teams Seeder"
Cohesion: 0.60
Nodes (3): port_healthy(), seed-more-teams.sh script, wait_healthy()

### Community 86 - "E2E Smoke Test"
Cohesion: 0.60
Nodes (3): post_github(), smoke-e2e.sh script, wait_healthy()

### Community 88 - "Seed Event Generator"
Cohesion: 0.83
Nodes (3): iso(), main(), datetime

### Community 89 - "Backend Start Script"
Cohesion: 0.83
Nodes (3): start-backend.sh script, start(), wait_healthy()

## Knowledge Gaps
- **202 isolated node(s):** `TileSpec`, `AgingPr`, `AuditEntry`, `ConnectorStatus`, `InvestmentSlice` (+197 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **30 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `JiraDashboardQueryService` connect `Code Review Analytics API` to `Connector Admin & Auto-Refresh Services`, `Admin REST Controllers`, `JDBC Audit & Team Repos`?**
  _High betweenness centrality (0.024) - this node is a cross-community bridge._
- **Why does `ConnectorAdminService` connect `Connector Admin Controller` to `Connector Admin & Auto-Refresh Services`, `JDBC Audit & Team Repos`, `Admin REST Controllers`?**
  _High betweenness centrality (0.020) - this node is a cross-community bridge._
- **Why does `EventEnvelope` connect `GitHub Event Publisher` to `Identity Resolution & Connector Event Wiring`, `Jenkins Backfill Service`, `GitHub Backfill Service`, `RabbitMQ Event Publishing (Connectors)`, `Connector Admin & Auto-Refresh Services`, `API Security & RBAC`, `AI Telemetry Backfill`, `GitLab Backfill Service (Java)`, `API Security & RBAC`, `Jenkins/Jira Backfill`?**
  _High betweenness centrality (0.020) - this node is a cross-community bridge._
- **What connects `TileSpec`, `AgingPr`, `AuditEntry` to the rest of the system?**
  _202 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Identity Resolution & Connector Event Wiring` be split into smaller, more focused modules?**
  _Cohesion score 0.057871692366266894 - nodes in this community are weakly interconnected._
- **Should `Code Review Analytics API` be split into smaller, more focused modules?**
  _Cohesion score 0.07597402597402597 - nodes in this community are weakly interconnected._
- **Should `Staging Writer Integration Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.09959183673469388 - nodes in this community are weakly interconnected._