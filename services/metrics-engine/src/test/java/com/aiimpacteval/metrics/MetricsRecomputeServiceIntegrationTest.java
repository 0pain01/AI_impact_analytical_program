package com.aiimpacteval.metrics;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Metric computations are the crown jewels (engineering standards §4) — verified against real
 * Postgres. Skipped when Docker is unusable locally (Docker Desktop API gating); must run in CI.
 * Covers: deployment rule matching, failed/non-deploy exclusion, latest-state dedup across
 * webhook + snapshot versions, merge-day attribution, p50 cycle time, org + team rollups.
 */
@Testcontainers(disabledWithoutDocker = true)
class MetricsRecomputeServiceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private MetricsRecomputeService service;

    private static final Instant NOW = Instant.now();

    @BeforeAll
    static void createSchema() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        // Mirrors api-core migrations V1 (staging.raw_event, core.team), V2 (mart.metric_daily),
        // and V4 (core.team_repo, mart scope_id/scope_type rename).
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS staging");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS mart");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS core");
        jdbc.execute("""
                CREATE TABLE staging.raw_event (
                    id BIGSERIAL PRIMARY KEY, source TEXT NOT NULL, source_id TEXT NOT NULL,
                    event_type TEXT NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    connector_version TEXT NOT NULL, payload JSONB NOT NULL,
                    CONSTRAINT uq_raw_event_natural_key UNIQUE (source, source_id, event_type))
                """);
        jdbc.execute("""
                CREATE TABLE mart.metric_daily (
                    id BIGSERIAL PRIMARY KEY, metric_key TEXT NOT NULL, scope_id TEXT NOT NULL,
                    scope_type TEXT NOT NULL DEFAULT 'repo', day DATE NOT NULL, value NUMERIC NOT NULL,
                    sample_size INT NOT NULL DEFAULT 0, metric_logic_version TEXT NOT NULL,
                    computed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    CONSTRAINT uq_metric_daily UNIQUE (metric_key, scope_id, day))
                """);
        jdbc.execute("""
                CREATE TABLE core.team (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name TEXT NOT NULL,
                    parent_team_id UUID, source TEXT, source_id TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    CONSTRAINT uq_team_source UNIQUE (source, source_id))
                """);
        jdbc.execute("""
                CREATE TABLE core.team_repo (
                    team_id UUID NOT NULL REFERENCES core.team (id), repo TEXT NOT NULL,
                    PRIMARY KEY (team_id, repo))
                """);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM staging.raw_event");
        jdbc.update("DELETE FROM mart.metric_daily");
        jdbc.update("DELETE FROM core.team_repo");
        jdbc.update("DELETE FROM core.team");
        service = new MetricsRecomputeService(jdbc, 90, "deploy|release", "hotfix|rollback|revert");
    }

    private java.util.UUID createTeam(String repo) {
        java.util.UUID teamId = jdbc.queryForObject(
                "INSERT INTO core.team (name, source, source_id) VALUES (?, 'github', ?) RETURNING id",
                java.util.UUID.class, "Team for " + repo, "team-" + repo);
        jdbc.update("INSERT INTO core.team_repo (team_id, repo) VALUES (?, ?)", teamId, repo);
        return teamId;
    }

    @Test
    void countsOnlySuccessfulDeployWorkflowsOncePerRun() {
        String day = NOW.minus(1, ChronoUnit.DAYS).toString();
        // Same run delivered twice (webhook + backfill snapshot) — must count once.
        insertEvent("wh-1", "workflow_run", workflowRunWebhook(101, "Deploy production", "success", day, "acme/app"));
        insertEvent("workflow_run:101:x", "workflow_run.snapshot", workflowRunSnapshot(101, "Deploy production", "success", day, "acme/app"));
        // Failed deploy and a non-deploy workflow — excluded.
        insertEvent("wh-2", "workflow_run", workflowRunWebhook(102, "Deploy production", "failure", day, "acme/app"));
        insertEvent("wh-3", "workflow_run", workflowRunWebhook(103, "CI tests", "success", day, "acme/app"));
        // Release workflow on another repo — counted, and rolls up to org.
        insertEvent("wh-4", "workflow_run", workflowRunWebhook(104, "Release v2", "success", day, "acme/lib"));

        service.recomputeAll();

        assertEquals(new BigDecimal("1"), metric("deployment_frequency", "acme/app", day));
        assertEquals(new BigDecimal("1"), metric("deployment_frequency", "acme/lib", day));
        assertEquals(new BigDecimal("2"), metric("deployment_frequency", "*", day));
    }

    @Test
    void prVelocityUsesLatestStateAndMergeDay() {
        String opened = NOW.minus(50, ChronoUnit.HOURS).toString();
        String merged = NOW.minus(2, ChronoUnit.HOURS).toString();
        // PR first seen open (no merged_at), later merged — latest state wins, counted once.
        insertEventAt("wh-10", "pull_request", pr(7, "acme/app", opened, null), NOW.minus(3, ChronoUnit.HOURS));
        insertEventAt("wh-11", "pull_request", pr(7, "acme/app", opened, merged), NOW.minus(1, ChronoUnit.HOURS));
        // A PR that is still open — not in velocity.
        insertEvent("wh-12", "pull_request", pr(8, "acme/app", opened, null));

        service.recomputeAll();

        assertEquals(new BigDecimal("1"), metric("pr_velocity", "acme/app", merged));
        assertEquals(new BigDecimal("1"), metric("pr_velocity", "*", merged));
        assertNull(metric("pr_velocity", "acme/app", opened));
    }

    @Test
    void cycleTimeMedianAcrossPrs() {
        String merged = NOW.minus(1, ChronoUnit.HOURS).toString();
        // Cycle times: 10h, 20h, 60h → p50 = 20h.
        insertEvent("s-1", "pull_request.snapshot", prSnapshot(21, "acme/app", hoursBefore(merged, 10), merged));
        insertEvent("s-2", "pull_request.snapshot", prSnapshot(22, "acme/app", hoursBefore(merged, 20), merged));
        insertEvent("s-3", "pull_request.snapshot", prSnapshot(23, "acme/app", hoursBefore(merged, 60), merged));

        service.recomputeAll();

        assertEquals(0, new BigDecimal("20").compareTo(metric("pr_cycle_time_p50_hours", "acme/app", merged)));
        assertEquals(3, jdbc.queryForObject(
                "SELECT sample_size FROM mart.metric_daily WHERE metric_key='pr_cycle_time_p50_hours' AND scope_id='*'",
                Integer.class));
    }

    @Test
    void leadTimeLinksPrToFirstDeployAfterMerge() {
        // PR opened 48h ago, merged 40h ago; a production deploy 38h ago on the same repo.
        // Lead time = deploy − created = 10h, bucketed on the deploy's day.
        String created = NOW.minus(48, ChronoUnit.HOURS).toString();
        String merged = NOW.minus(40, ChronoUnit.HOURS).toString();
        String deployTs = NOW.minus(38, ChronoUnit.HOURS).toString();
        insertEvent("pr-lead", "pull_request.snapshot", prSnapshot(30, "acme/app", created, merged));
        insertEvent("dep-lead", "workflow_run", workflowRunWebhook(301, "Deploy production", "success", deployTs, "acme/app"));

        service.recomputeAll();

        assertEquals(0, new BigDecimal("10").compareTo(metric("lead_time_p50_hours", "acme/app", deployTs)));
    }

    @Test
    void changeFailureRateCountsDeployFollowedByHotfix() {
        String day = NOW.minus(5, ChronoUnit.HOURS).toString();
        // Two deploys same day; the first is followed by a hotfix within 48h → failed. CFR = 0.5.
        insertEvent("d1", "workflow_run", workflowRunWebhook(401, "Deploy production", "success",
                NOW.minus(5, ChronoUnit.HOURS).toString(), "acme/app"));
        insertEvent("d2", "workflow_run", workflowRunWebhook(402, "Deploy production", "success",
                NOW.minus(4, ChronoUnit.HOURS).toString(), "acme/app"));
        insertEvent("h1", "workflow_run", workflowRunWebhook(403, "Hotfix rollback", "success",
                NOW.minus(3, ChronoUnit.HOURS).toString(), "acme/app"));

        service.recomputeAll();

        assertEquals(0, new BigDecimal("0.5").compareTo(metric("change_failure_rate", "acme/app", day)));
        assertEquals(2, jdbc.queryForObject(
                "SELECT sample_size FROM mart.metric_daily WHERE metric_key='change_failure_rate' AND scope_id='acme/app'",
                Integer.class));
    }

    @Test
    void mttrMeasuresDeployToRemediation() {
        // Failed deploy at T-6h, hotfix at T-3h → MTTR 3h.
        insertEvent("md", "workflow_run", workflowRunWebhook(501, "Deploy production", "success",
                NOW.minus(6, ChronoUnit.HOURS).toString(), "acme/app"));
        insertEvent("mh", "workflow_run", workflowRunWebhook(502, "Rollback", "success",
                NOW.minus(3, ChronoUnit.HOURS).toString(), "acme/app"));

        service.recomputeAll();

        String day = NOW.minus(6, ChronoUnit.HOURS).toString();
        assertEquals(0, new BigDecimal("3").compareTo(metric("mttr_p50_hours", "acme/app", day)));
    }

    @Test
    void healthyDeployHasZeroCfrAndNoMttr() {
        String day = NOW.minus(5, ChronoUnit.HOURS).toString();
        insertEvent("ok", "workflow_run", workflowRunWebhook(601, "Deploy production", "success",
                NOW.minus(5, ChronoUnit.HOURS).toString(), "acme/app"));

        service.recomputeAll();

        assertEquals(0, BigDecimal.ZERO.compareTo(metric("change_failure_rate", "acme/app", day)));
        assertNull(metric("mttr_p50_hours", "acme/app", day));
    }

    @Test
    void teamRollupSumsCountsAcrossItsRepos() {
        // A team owns two repos; deploy on each → team-level deployment_frequency = 2.
        var teamId = createTeam("acme/app");
        jdbc.update("INSERT INTO core.team_repo (team_id, repo) VALUES (?, ?)", teamId, "acme/lib");
        String day = NOW.minus(2, ChronoUnit.HOURS).toString();
        insertEvent("t1", "workflow_run", workflowRunWebhook(701, "Deploy production", "success",
                NOW.minus(2, ChronoUnit.HOURS).toString(), "acme/app"));
        insertEvent("t2", "workflow_run", workflowRunWebhook(702, "Deploy production", "success",
                NOW.minus(1, ChronoUnit.HOURS).toString(), "acme/lib"));
        // A deploy on an unmapped repo must not leak into the team's rollup.
        insertEvent("t3", "workflow_run", workflowRunWebhook(703, "Deploy production", "success",
                NOW.minus(1, ChronoUnit.HOURS).toString(), "acme/unmapped"));

        service.recomputeAll();

        assertEquals(0, new BigDecimal("2").compareTo(metric("deployment_frequency", teamId.toString(), day)));
    }

    @Test
    void teamRollupComputesTruePercentileNotAverageOfRepoMedians() {
        // Team owns two repos; cycle times per repo would each median differently, but the
        // team median must be computed over ALL underlying PRs, not by averaging repo medians.
        var teamId = createTeam("acme/app");
        jdbc.update("INSERT INTO core.team_repo (team_id, repo) VALUES (?, ?)", teamId, "acme/lib");
        String merged = NOW.minus(1, ChronoUnit.HOURS).toString();
        // acme/app: 10h, 10h (median 10) — acme/lib: 100h (median 100).
        // Combined team population {10,10,100}: true median = 10, NOT avg(10,100)=55.
        insertEvent("tc-1", "pull_request.snapshot", prSnapshot(51, "acme/app", hoursBefore(merged, 10), merged));
        insertEvent("tc-2", "pull_request.snapshot", prSnapshot(52, "acme/app", hoursBefore(merged, 10), merged));
        insertEvent("tc-3", "pull_request.snapshot", prSnapshot(53, "acme/lib", hoursBefore(merged, 100), merged));

        service.recomputeAll();

        assertEquals(0, new BigDecimal("10").compareTo(metric("pr_cycle_time_p50_hours", teamId.toString(), merged)));
        assertEquals(3, jdbc.queryForObject(
                "SELECT sample_size FROM mart.metric_daily WHERE metric_key='pr_cycle_time_p50_hours' AND scope_id=?",
                Integer.class, teamId.toString()));
    }

    @Test
    void repoWithNoTeamProducesNoTeamRow() {
        String day = NOW.minus(2, ChronoUnit.HOURS).toString();
        insertEvent("nt1", "workflow_run", workflowRunWebhook(801, "Deploy production", "success",
                NOW.minus(2, ChronoUnit.HOURS).toString(), "acme/orphan"));

        service.recomputeAll();

        Integer teamRows = jdbc.queryForObject(
                "SELECT count(*) FROM mart.metric_daily WHERE metric_key='deployment_frequency' AND scope_type='team'",
                Integer.class);
        assertEquals(0, teamRows);
        assertEquals(0, new BigDecimal("1").compareTo(metric("deployment_frequency", "acme/orphan", day)));
    }

    @Test
    void recomputeIsIdempotent() {
        String day = NOW.minus(1, ChronoUnit.DAYS).toString();
        insertEvent("wh-20", "workflow_run", workflowRunWebhook(201, "Deploy", "success", day, "acme/app"));

        service.recomputeAll();
        service.recomputeAll();

        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM mart.metric_daily WHERE metric_key='deployment_frequency' AND scope_id='acme/app'",
                Integer.class));
    }

    // --- fixtures ---

    private static String hoursBefore(String instant, long hours) {
        return Instant.parse(instant).minus(hours, ChronoUnit.HOURS).toString();
    }

    private void insertEvent(String sourceId, String eventType, String payload) {
        insertEventAt(sourceId, eventType, payload, NOW);
    }

    private void insertEventAt(String sourceId, String eventType, String payload, Instant receivedAt) {
        jdbc.update("""
                INSERT INTO staging.raw_event (source, source_id, event_type, received_at, connector_version, payload)
                VALUES ('github', ?, ?, ?, '0.1.0', ?::jsonb)
                """, sourceId, eventType, java.sql.Timestamp.from(receivedAt), payload);
    }

    private static String workflowRunWebhook(long id, String name, String conclusion, String updatedAt, String repo) {
        return """
                {"action":"completed","workflow_run":{"id":%d,"name":"%s","conclusion":"%s","updated_at":"%s"},
                 "repository":{"full_name":"%s"}}""".formatted(id, name, conclusion, updatedAt, repo);
    }

    private static String workflowRunSnapshot(long id, String name, String conclusion, String updatedAt, String repo) {
        return """
                {"id":%d,"name":"%s","conclusion":"%s","updated_at":"%s","repository":{"full_name":"%s"}}"""
                .formatted(id, name, conclusion, updatedAt, repo);
    }

    private static String pr(long id, String repo, String createdAt, String mergedAt) {
        return """
                {"action":"x","pull_request":%s}""".formatted(prSnapshot(id, repo, createdAt, mergedAt));
    }

    private static String prSnapshot(long id, String repo, String createdAt, String mergedAt) {
        String mergedJson = mergedAt == null ? "null" : "\"" + mergedAt + "\"";
        return """
                {"id":%d,"created_at":"%s","merged_at":%s,"base":{"repo":{"full_name":"%s"}}}"""
                .formatted(id, createdAt, mergedJson, repo);
    }

    private BigDecimal metric(String key, String scopeId, String dayInstant) {
        var rows = jdbc.queryForList(
                "SELECT value FROM mart.metric_daily WHERE metric_key=? AND scope_id=? AND day=(?::timestamptz AT TIME ZONE 'UTC')::date",
                BigDecimal.class, key, scopeId, dayInstant);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
