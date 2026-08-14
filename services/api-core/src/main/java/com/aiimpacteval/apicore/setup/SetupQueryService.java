package com.aiimpacteval.apicore.setup;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Onboarding readiness (PRD E1-S5). Every field is derived from {@code staging.raw_event} /
 * {@code mart.metric_daily} timestamps already written by the ingestion and metrics pipelines —
 * never a manually-maintained flag (BRD rule: no manual tagging).
 */
@Service
public class SetupQueryService {

    private static final int TARGET_MINUTES = 30;

    private final JdbcTemplate jdbcTemplate;

    public SetupQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SetupStatus getStatus() {
        boolean gitConnected = exists(
                "SELECT 1 FROM staging.raw_event WHERE source = 'github' "
                        + "AND event_type NOT LIKE 'workflow_run%' AND event_type NOT LIKE 'deployment_status%'");
        boolean ciConnected = exists(
                "SELECT 1 FROM staging.raw_event WHERE source = 'github' "
                        + "AND (event_type LIKE 'workflow_run%' OR event_type LIKE 'deployment_status%')");
        boolean ticketingConnected = exists("SELECT 1 FROM staging.raw_event WHERE source = 'jira'");
        boolean dashboardReady = exists("SELECT 1 FROM mart.metric_daily");

        Instant firstConnectionAt = queryInstant(
                "SELECT MIN(received_at) FROM staging.raw_event WHERE source IN ('github', 'jira')");
        Instant firstDashboardAt = queryInstant("SELECT MIN(computed_at) FROM mart.metric_daily");

        Long minutesToValue = null;
        Boolean withinTarget = null;
        if (firstConnectionAt != null && firstDashboardAt != null) {
            minutesToValue = Duration.between(firstConnectionAt, firstDashboardAt).toMinutes();
            withinTarget = minutesToValue <= TARGET_MINUTES;
        }

        List<ChecklistItem> checklist = List.of(
                new ChecklistItem("git", "Connect a Git provider", gitConnected,
                        gitConnected ? "Receiving PR/commit events" : "No GitHub PR or commit events ingested yet"),
                new ChecklistItem("ticketing", "Connect ticketing (Jira)", ticketingConnected,
                        ticketingConnected ? "Receiving issue events" : "No Jira issue events ingested yet"),
                new ChecklistItem("ci", "Connect a CI/CD tool", ciConnected,
                        ciConnected ? "Receiving workflow run events" : "No GitHub Actions workflow events ingested yet"),
                new ChecklistItem("dashboard", "First dashboard populated", dashboardReady,
                        dashboardReady ? "Cockpit metrics have been computed" : "Metrics engine has not computed any values yet"));

        return new SetupStatus(checklist, firstConnectionAt, firstDashboardAt, minutesToValue, withinTarget, TARGET_MINUTES);
    }

    private boolean exists(String innerSql) {
        Boolean result = jdbcTemplate.queryForObject("SELECT EXISTS(" + innerSql + ")", Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    private Instant queryInstant(String sql) {
        Timestamp ts = jdbcTemplate.queryForObject(sql, Timestamp.class);
        return ts == null ? null : ts.toInstant();
    }

    public record ChecklistItem(String key, String label, boolean done, String detail) {
    }

    public record SetupStatus(
            List<ChecklistItem> checklist,
            Instant firstConnectionAt,
            Instant firstDashboardAt,
            Long minutesToValue,
            Boolean withinTarget,
            int targetMinutes) {
    }
}
