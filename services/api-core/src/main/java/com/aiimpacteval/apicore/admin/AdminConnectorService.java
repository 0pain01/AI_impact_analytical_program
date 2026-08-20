package com.aiimpacteval.apicore.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Connector health for the Admin console (PRD E1-S4/E8). Every field is derived from
 * {@code staging.raw_event} timestamps already written by ingestion — never a manually-maintained
 * status flag (BRD rule: no manual tagging). Mirrors the derivation approach used for the
 * onboarding checklist ({@code SetupQueryService}).
 *
 * <p>Only connectors actually wired into ingestion are reported here. Connectors named in the PRD
 * but not yet built (SonarQube, PagerDuty, AI-assistant telemetry, GitLab) are intentionally
 * absent rather than faked — the Admin UI should say "not built yet", not show fabricated health.
 */
@Service
public class AdminConnectorService {

    /** No event within this window counts as stale, even though the connector has synced before. */
    private static final Duration STALE_AFTER = Duration.ofHours(24);

    private final JdbcTemplate jdbcTemplate;

    public AdminConnectorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ConnectorHealth> listConnectors() {
        Instant now = Instant.now();
        return List.of(
                health("github", "GitHub", "Git host",
                        "source = 'github' AND event_type NOT LIKE 'workflow_run%' AND event_type NOT LIKE 'deployment_status%'",
                        now),
                health("github_actions", "GitHub Actions", "CI/CD",
                        "source = 'github' AND (event_type LIKE 'workflow_run%' OR event_type LIKE 'deployment_status%')",
                        now),
                health("jira", "Jira", "Ticketing", "source = 'jira'", now),
                health("jenkins", "Jenkins", "CI/CD", "source = 'jenkins'", now));
    }

    private ConnectorHealth health(String key, String name, String type, String whereClause, Instant now) {
        Instant lastSyncAt = queryInstant("SELECT MAX(received_at) FROM staging.raw_event WHERE " + whereClause);
        long eventCount = queryCount("SELECT COUNT(*) FROM staging.raw_event WHERE " + whereClause);
        ConnectorStatus status = deriveStatus(lastSyncAt, now);
        return new ConnectorHealth(key, name, type, status, lastSyncAt, eventCount);
    }

    private ConnectorStatus deriveStatus(Instant lastSyncAt, Instant now) {
        if (lastSyncAt == null) {
            return ConnectorStatus.NOT_CONNECTED;
        }
        return Duration.between(lastSyncAt, now).compareTo(STALE_AFTER) > 0
                ? ConnectorStatus.STALE
                : ConnectorStatus.CONNECTED;
    }

    private Instant queryInstant(String sql) {
        Timestamp ts = jdbcTemplate.queryForObject(sql, Timestamp.class);
        return ts == null ? null : ts.toInstant();
    }

    private long queryCount(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    public enum ConnectorStatus {
        CONNECTED, STALE, NOT_CONNECTED
    }

    public record ConnectorHealth(
            String key, String name, String type, ConnectorStatus status, Instant lastSyncAt, long eventCount) {
    }
}
