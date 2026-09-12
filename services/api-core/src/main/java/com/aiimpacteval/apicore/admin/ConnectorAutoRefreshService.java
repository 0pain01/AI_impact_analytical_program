package com.aiimpacteval.apicore.admin;

import com.aiimpacteval.common.http.TimeoutRestClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Periodically re-triggers backfill for every Jira project / Jenkins job this platform already
 * knows about, so their Admin console health never requires a human to click "Refresh" just to
 * prove the connector still works (PRD E1-S4/E8; BRD "derived automatically, never require
 * engineers to change how they work"). Unlike GitHub/GitLab, connector-jira and connector-jenkins
 * have no live webhook path wired up in this deployment (see connector-jenkins's README) — the
 * only way {@code staging.connector_activity.last_checked_at} ever advances for them is a
 * backfill call, manual or otherwise, so without this, both inevitably age past
 * {@code AdminConnectorService.STALE_AFTER} (24h) the moment nobody happens to click Refresh.
 *
 * <p>This deliberately re-runs the *same* backfill endpoints a manual "Refresh" click already
 * hits (ADR-0002: api-core owns no ingestion logic, only triggers the connector's own). Both
 * {@code JiraBackfillService}/{@code JenkinsBackfillService} republish a snapshot event for every
 * issue/build in scope on every call, not only deltas — {@code StagingEventWriter}'s
 * {@code connector_activity} upsert advances on every event it processes, duplicates included
 * (V11 migration), so even a cycle that finds zero real changes still answers "yes, we checked,
 * just now" rather than leaving the timestamp frozen. No new heartbeat mechanism was needed.
 *
 * <p>Deliberately NOT routed through {@link ConnectorAdminService}'s {@code triggers} map or
 * {@link com.aiimpacteval.apicore.audit.AuditLog}: those exist for admin-initiated, user-visible
 * actions (connect, disconnect, org/group import) — BRD's audit rule covers configuration
 * changes, access grants, and data exports, none of which a routine background health-check is.
 * Logging a WARN per failed item is enough for an operator to notice a genuinely broken
 * connection without an audit-log entry every cycle for every known project/job.
 *
 * <p>"Known" project keys/jobs are discovered from data already staged rather than a new
 * persistent "connected sources" table — {@code staging.jira_issue_state.project_key} and the
 * {@code jenkins:{jobName}:{buildNumber}} shape of {@code raw_event.source_id} (connector-
 * jenkins's own idempotency key, ADR-0003) already answer "what has ever been connected" without
 * new schema or cross-connector coupling.
 */
@Service
public class ConnectorAutoRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorAutoRefreshService.class);

    // Generous, matching ConnectorAdminService's connector-call timeout — a slow Jira/Jenkins
    // instance shouldn't make this cycle hang the JVM's shared scheduler thread pool forever.
    private static final Duration CONNECTOR_CLIENT_READ_TIMEOUT = Duration.ofMinutes(10);

    private final RestClient jiraClient;
    private final RestClient jenkinsClient;
    private final JdbcTemplate jdbcTemplate;

    public ConnectorAutoRefreshService(RestClient.Builder restClientBuilder,
                                       @Value("${connectors.jira.base-url}") String jiraBaseUrl,
                                       @Value("${connectors.jenkins.base-url}") String jenkinsBaseUrl,
                                       JdbcTemplate jdbcTemplate) {
        this.jiraClient = TimeoutRestClients.withTimeouts(restClientBuilder, Duration.ofSeconds(10), CONNECTOR_CLIENT_READ_TIMEOUT)
                .baseUrl(jiraBaseUrl)
                .build();
        this.jenkinsClient = TimeoutRestClients.withTimeouts(restClientBuilder, Duration.ofSeconds(10), CONNECTOR_CLIENT_READ_TIMEOUT)
                .baseUrl(jenkinsBaseUrl)
                .build();
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${connectors.auto-refresh-interval-ms:1800000}", initialDelay = 20_000)
    public void refreshKnownJiraAndJenkinsSources() {
        List<String> projectKeys = knownJiraProjectKeys();
        for (String projectKey : projectKeys) {
            try {
                jiraClient.post().uri("/internal/backfill?projectKey={projectKey}", projectKey)
                        .retrieve().toBodilessEntity();
                log.info("Auto-refresh: Jira project {} re-checked", projectKey);
            } catch (Exception e) {
                log.warn("Auto-refresh: Jira project {} re-check failed: {}", projectKey, e.getMessage());
            }
        }

        List<String> jobNames = knownJenkinsJobNames();
        for (String jobName : jobNames) {
            try {
                jenkinsClient.post().uri("/internal/backfill?jobName={jobName}", jobName)
                        .retrieve().toBodilessEntity();
                log.info("Auto-refresh: Jenkins job {} re-checked", jobName);
            } catch (Exception e) {
                log.warn("Auto-refresh: Jenkins job {} re-check failed: {}", jobName, e.getMessage());
            }
        }

        if (projectKeys.isEmpty() && jobNames.isEmpty()) {
            log.debug("Auto-refresh: no known Jira projects or Jenkins jobs to re-check yet");
        }
    }

    private List<String> knownJiraProjectKeys() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT project_key FROM staging.jira_issue_state WHERE project_key <> 'unknown'",
                String.class);
    }

    // connector-jenkins's own idempotency key is "jenkins:{jobName}:{buildNumber}" (see its
    // JenkinsBackfillService javadoc) — splitting raw_event.source_id on ':' recovers the job
    // name without needing a dedicated column or a call back into connector-jenkins itself.
    private List<String> knownJenkinsJobNames() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT split_part(source_id, ':', 2) FROM staging.raw_event WHERE source = 'jenkins'",
                String.class);
    }
}
