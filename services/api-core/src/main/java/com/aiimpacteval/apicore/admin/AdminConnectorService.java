package com.aiimpacteval.apicore.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Connector health for the Admin console (PRD E1-S4/E8). Every field is derived from
 * {@code staging.raw_event}/{@code staging.connector_activity} timestamps already written by
 * ingestion — never a manually-maintained status flag (BRD rule: no manual tagging). Mirrors the
 * derivation approach used for the onboarding checklist ({@code SetupQueryService}).
 *
 * <p>Two different timestamps are reported, and they answer different questions:
 * {@code lastDataChangeAt} (from {@code raw_event}) is "when did something actually change" —
 * it only advances when a genuinely new event lands, since idempotent redeliveries/re-checks are
 * no-ops by design (ADR-0003). {@code lastCheckedAt} (from {@code connector_activity}, V11) is
 * "when did we last hear from this source at all" — it advances on every event, including exact
 * duplicates. Before V11, {@code status}/staleness was derived from {@code lastDataChangeAt}
 * alone, which made a perfectly healthy connector look dead the moment its upstream data ran out
 * of changes to report (e.g. Jira re-checking issues nobody has touched) — {@code status} is now
 * derived from {@code lastCheckedAt} instead, so "is this connector actually working" and "has
 * anything changed lately" are no longer conflated into one misleading signal.
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
                health("github", "GitHub", "Git host", "github",
                        "source = 'github' AND event_type NOT LIKE 'workflow_run%' AND event_type NOT LIKE 'deployment_status%'",
                        now),
                health("github_actions", "GitHub Actions", "CI/CD", "github",
                        "source = 'github' AND (event_type LIKE 'workflow_run%' OR event_type LIKE 'deployment_status%')",
                        now),
                health("jira", "Jira", "Ticketing", "jira", "source = 'jira'", now),
                health("jenkins", "Jenkins", "CI/CD", "jenkins", "source = 'jenkins'", now));
    }

    private ConnectorHealth health(String key, String name, String type, String source, String whereClause, Instant now) {
        Instant lastDataChangeAt = queryInstant("SELECT MAX(received_at) FROM staging.raw_event WHERE " + whereClause);
        Instant lastCheckedAt = queryLastChecked(source);
        long eventCount = queryCount("SELECT COUNT(*) FROM staging.raw_event WHERE " + whereClause);
        ConnectorStatus status = deriveStatus(lastCheckedAt, now);
        return new ConnectorHealth(key, name, type, status, lastDataChangeAt, lastCheckedAt, eventCount);
    }

    private ConnectorStatus deriveStatus(Instant lastCheckedAt, Instant now) {
        if (lastCheckedAt == null) {
            return ConnectorStatus.NOT_CONNECTED;
        }
        return Duration.between(lastCheckedAt, now).compareTo(STALE_AFTER) > 0
                ? ConnectorStatus.STALE
                : ConnectorStatus.CONNECTED;
    }

    private Instant queryInstant(String sql) {
        Timestamp ts = jdbcTemplate.queryForObject(sql, Timestamp.class);
        return ts == null ? null : ts.toInstant();
    }

    private Instant queryLastChecked(String source) {
        return jdbcTemplate.query(
                        "SELECT last_checked_at FROM staging.connector_activity WHERE source = ?",
                        rs -> rs.next() ? rs.getTimestamp("last_checked_at").toInstant() : null,
                        source);
    }

    private long queryCount(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    public enum ConnectorStatus {
        CONNECTED, STALE, NOT_CONNECTED
    }

    public record ConnectorHealth(
            String key, String name, String type, ConnectorStatus status,
            Instant lastDataChangeAt, Instant lastCheckedAt, long eventCount) {
    }
}
