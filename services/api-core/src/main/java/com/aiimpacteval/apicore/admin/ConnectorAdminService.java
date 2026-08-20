package com.aiimpacteval.apicore.admin;

import com.aiimpacteval.apicore.audit.AuditLog;
import com.aiimpacteval.apicore.audit.AuditLog.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lets an ADMIN connect a new GitHub repo (optionally assigning it to a team in the same step),
 * import a whole org's teams, or manually check per-repo sync status — from the Admin console
 * instead of calling connector-github's internal backfill endpoints from a terminal (PRD
 * E1-S4/E8). api-core owns no ingestion logic itself (ADR-0002) — every "connect" call here only
 * triggers the existing connector's backfill over HTTP.
 *
 * <p>Backfill runs asynchronously — a repo backfill can legitimately take several minutes
 * (paginated PR/commit/workflow-run history, one extra API call per PR for reviews), and
 * blocking the request thread for that long would make the UI look hung. {@link #triggers} is
 * an in-memory (single-instance — fine for this deployment shape, ADR-0002) tracker of the most
 * recent trigger per repo, purely so {@link #listRepoSyncStatus()} can tell "still syncing" apart
 * from "synced" apart from "failed" without polling connector-github itself or standing up a
 * persistent job table. It's a UX signal, not a source of truth — {@code staging.raw_event}
 * (surfaced here via {@code pull_request_state}/{@code workflow_run_state}) remains that.
 */
@Service
public class ConnectorAdminService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorAdminService.class);

    public enum SyncState {
        IN_PROGRESS, COMPLETED, FAILED
    }

    private record Trigger(SyncState state, Instant at, String error) {
    }

    public record RepoSyncStatus(String repo, Instant lastSyncAt, long eventCount, SyncState syncState,
                                 String syncError, List<String> teams) {
    }

    private final RestClient githubClient;
    private final AuditLog auditLog;
    private final JdbcTemplate jdbcTemplate;
    private final TeamAdminService teamAdminService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Trigger> triggers = new ConcurrentHashMap<>();

    public ConnectorAdminService(RestClient.Builder restClientBuilder,
                                 @Value("${connectors.github.base-url}") String githubBaseUrl,
                                 AuditLog auditLog, JdbcTemplate jdbcTemplate, TeamAdminService teamAdminService) {
        this.githubClient = restClientBuilder.baseUrl(githubBaseUrl).build();
        this.auditLog = auditLog;
        this.jdbcTemplate = jdbcTemplate;
        this.teamAdminService = teamAdminService;
    }

    /**
     * Connects a repo (triggers backfill) and, if {@code teamId} is given, assigns it to that
     * team in the same call — the two used to be two separate manual steps (connect via
     * terminal, then a raw SQL insert to map it to a team); this is the single "process to add a
     * repo and then add it to the team" the Admin UI now drives end to end.
     */
    public void connectRepo(String actorEmail, String owner, String repo, UUID teamId, String sourceIp) {
        String fullName = owner + "/" + repo;
        if (teamId != null) {
            teamAdminService.addRepo(teamId, fullName);
        }
        auditLog.write(new AuditEvent(actorEmail, "REPO_CONNECT_TRIGGERED", "repo", fullName,
                null, teamId == null ? null : "{\"teamId\":\"" + teamId + "\"}", sourceIp));
        triggers.put(fullName, new Trigger(SyncState.IN_PROGRESS, Instant.now(), null));
        executor.submit(() -> {
            try {
                githubClient.post()
                        .uri("/internal/backfill?owner={owner}&repo={repo}", owner, repo)
                        .retrieve()
                        .toBodilessEntity();
                triggers.put(fullName, new Trigger(SyncState.COMPLETED, Instant.now(), null));
                log.info("Repo connect backfill for {} completed", fullName);
            } catch (Exception e) {
                triggers.put(fullName, new Trigger(SyncState.FAILED, Instant.now(), e.getMessage()));
                log.warn("Repo connect backfill for {} failed: {}", fullName, e.getMessage());
            }
        });
    }

    /**
     * Removes a repo from every view Cockpit/Admin actually reads: the three typed "latest
     * state" projections ({@code pull_request_state}/{@code workflow_run_state}/
     * {@code pull_request_review_state}) and any {@code core.team_repo} mapping. Deliberately
     * does NOT touch {@code staging.raw_event} — that's the immutable, append-only source of
     * truth this system is built around (architecture doc §5.1, FR-1.8 replay/audit); this is a
     * "stop showing it" operation, not a GDPR-style erasure. Re-connecting the same repo later
     * re-derives the same projections from that untouched raw log (backfill re-publishes, or a
     * live webhook lands) — nothing is permanently lost.
     */
    public void disconnectRepo(String actorEmail, String repo, String sourceIp) {
        int prRows = jdbcTemplate.update("DELETE FROM staging.pull_request_state WHERE repo = ?", repo);
        int runRows = jdbcTemplate.update("DELETE FROM staging.workflow_run_state WHERE repo = ?", repo);
        int reviewRows = jdbcTemplate.update("DELETE FROM staging.pull_request_review_state WHERE repo = ?", repo);
        int teamRows = jdbcTemplate.update("DELETE FROM core.team_repo WHERE repo = ?", repo);
        triggers.remove(repo);
        auditLog.write(new AuditEvent(actorEmail, "REPO_DISCONNECTED", "repo", repo, null,
                "{\"prRows\":" + prRows + ",\"runRows\":" + runRows + ",\"reviewRows\":" + reviewRows
                        + ",\"teamMappingsRemoved\":" + teamRows + "}",
                sourceIp));
        log.info("Disconnected repo {}: {} PR rows, {} workflow-run rows, {} review rows, {} team mappings removed",
                repo, prRows, runRows, reviewRows, teamRows);
    }

    public void connectGithubOrgTeams(String actorEmail, String org, String sourceIp) {
        auditLog.write(new AuditEvent(actorEmail, "GITHUB_TEAMS_CONNECT_TRIGGERED", "github_org", org,
                null, null, sourceIp));
        executor.submit(() -> {
            try {
                githubClient.post()
                        .uri("/internal/backfill-teams?org={org}", org)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Team import backfill for org {} completed", org);
            } catch (Exception e) {
                log.warn("Team import backfill for org {} failed: {}", org, e.getMessage());
            }
        });
    }

    /**
     * One row per repo that's either ever landed data or was just triggered (so a genuinely
     * brand-new repo shows "Syncing" immediately rather than being invisible until its first
     * event lands). {@code lastSyncAt}/{@code eventCount} come from
     * {@code staging.pull_request_state}/{@code workflow_run_state} — the same typed projections
     * the DORA/PR metrics themselves read, not a re-derivation.
     */
    public List<RepoSyncStatus> listRepoSyncStatus() {
        Map<String, Object[]> dbInfo = new HashMap<>();
        jdbcTemplate.query("""
                SELECT repo, max(last_received_at) AS last_sync, count(*) AS cnt FROM (
                    SELECT repo, last_received_at FROM staging.pull_request_state
                    UNION ALL
                    SELECT repo, last_received_at FROM staging.workflow_run_state
                ) x
                WHERE repo <> 'unknown'
                GROUP BY repo
                """, rs -> {
            while (rs.next()) {
                dbInfo.put(rs.getString("repo"),
                        new Object[]{rs.getTimestamp("last_sync"), rs.getLong("cnt")});
            }
            return null;
        });

        Map<String, List<String>> teamsByRepo = new HashMap<>();
        jdbcTemplate.query("SELECT tr.repo, t.name FROM core.team_repo tr JOIN core.team t ON t.id = tr.team_id",
                rs -> {
                    while (rs.next()) {
                        teamsByRepo.computeIfAbsent(rs.getString("repo"), k -> new ArrayList<>()).add(rs.getString("name"));
                    }
                    return null;
                });

        TreeSet<String> allRepos = new TreeSet<>();
        allRepos.addAll(dbInfo.keySet());
        allRepos.addAll(triggers.keySet());

        List<RepoSyncStatus> result = new ArrayList<>();
        for (String repo : allRepos) {
            Object[] db = dbInfo.get(repo);
            Instant lastSyncAt = db == null ? null : ((Timestamp) db[0]).toInstant();
            long eventCount = db == null ? 0 : (Long) db[1];
            Trigger trigger = triggers.get(repo);

            SyncState state;
            String error = null;
            if (trigger != null && trigger.state() == SyncState.IN_PROGRESS) {
                state = SyncState.IN_PROGRESS;
            } else if (trigger != null && trigger.state() == SyncState.FAILED
                    && (lastSyncAt == null || trigger.at().isAfter(lastSyncAt))) {
                state = SyncState.FAILED;
                error = trigger.error();
            } else if (lastSyncAt != null) {
                state = SyncState.COMPLETED;
            } else {
                // Triggered but nothing landed and no failure recorded yet — treat as still syncing.
                state = SyncState.IN_PROGRESS;
            }

            result.add(new RepoSyncStatus(repo, lastSyncAt, eventCount, state, error,
                    teamsByRepo.getOrDefault(repo, List.of())));
        }
        return result;
    }
}
