package com.aiimpacteval.apicore.admin;

import com.aiimpacteval.apicore.audit.AuditLog;
import com.aiimpacteval.apicore.audit.AuditLog.AuditEvent;
import com.aiimpacteval.common.http.TimeoutRestClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.sql.Timestamp;
import java.time.Duration;
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
 *
 * <p>GitLab project/group connect follows the identical pattern against connector-gitlab, just
 * keyed under {@code "gitlab:" + project} in {@link #triggers} to match the prefix
 * {@code StagingEventWriter} stamps onto GitLab's rows in the same shared state tables (see its
 * class javadoc) — {@link #listRepoSyncStatus()} needs no source-specific branching as a result.
 *
 * <p>Jira project and Jenkins job connect/sync-status/disconnect follow the same trigger-and-poll
 * shape as {@link #connectRepo}, but track their own separate {@link #jiraTriggers}/
 * {@link #jenkinsTriggers} maps and expose their own list endpoints rather than folding into
 * {@link #listRepoSyncStatus()} — a Jira project key or Jenkins job name isn't a "repo" the way
 * {@code pull_request_state}/{@code workflow_run_state} key their rows, and (unlike GitHub/GitLab)
 * neither has a project→team mapping to assign in the same call (see
 * {@code JiraDashboardController}'s javadoc for why that gap exists). "Known" project keys/job
 * names for the auto-discovery a fresh backfill result feeds are queried the same way
 * {@link ConnectorAutoRefreshService} already does — see its javadoc.
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

    public record JiraProjectSyncStatus(String projectKey, Instant lastSyncAt, long eventCount,
                                        SyncState syncState, String syncError) {
    }

    public record JenkinsJobSyncStatus(String jobName, Instant lastSyncAt, long eventCount,
                                       SyncState syncState, String syncError) {
    }

    private final RestClient githubClient;
    private final RestClient gitlabClient;
    private final RestClient jiraClient;
    private final RestClient jenkinsClient;
    private final AuditLog auditLog;
    private final JdbcTemplate jdbcTemplate;
    private final TeamAdminService teamAdminService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Trigger> triggers = new ConcurrentHashMap<>();
    private final Map<String, Trigger> jiraTriggers = new ConcurrentHashMap<>();
    private final Map<String, Trigger> jenkinsTriggers = new ConcurrentHashMap<>();

    // This call blocks for the connector's ENTIRE backfill (large repos genuinely take several
    // minutes — the N+1 per-PR review fetch in GithubBackfillService, see its javadoc). 30
    // minutes is a backstop against a truly stuck connector call (e.g. GithubRestClients' own
    // 60s-per-request timeout somehow not firing), not a normal-case constraint — without it, a
    // hung connector-github call left this trigger stuck showing "Syncing" in the Admin console
    // forever, which is exactly the bug that surfaced this whole timeout gap. Shared by the
    // GitLab client below for the same reason.
    private static final Duration CONNECTOR_CLIENT_READ_TIMEOUT = Duration.ofMinutes(30);

    /** listRepoSyncStatus() treats an IN_PROGRESS trigger older than this as failed, not syncing. */
    private static final Duration STUCK_AFTER = Duration.ofMinutes(35);

    public ConnectorAdminService(RestClient.Builder restClientBuilder,
                                 @Value("${connectors.github.base-url}") String githubBaseUrl,
                                 @Value("${connectors.gitlab.base-url}") String gitlabBaseUrl,
                                 @Value("${connectors.jira.base-url}") String jiraBaseUrl,
                                 @Value("${connectors.jenkins.base-url}") String jenkinsBaseUrl,
                                 AuditLog auditLog, JdbcTemplate jdbcTemplate, TeamAdminService teamAdminService) {
        this.githubClient = TimeoutRestClients.withTimeouts(restClientBuilder, Duration.ofSeconds(10), CONNECTOR_CLIENT_READ_TIMEOUT)
                .baseUrl(githubBaseUrl)
                .build();
        this.gitlabClient = TimeoutRestClients.withTimeouts(restClientBuilder, Duration.ofSeconds(10), CONNECTOR_CLIENT_READ_TIMEOUT)
                .baseUrl(gitlabBaseUrl)
                .build();
        this.jiraClient = TimeoutRestClients.withTimeouts(restClientBuilder, Duration.ofSeconds(10), CONNECTOR_CLIENT_READ_TIMEOUT)
                .baseUrl(jiraBaseUrl)
                .build();
        this.jenkinsClient = TimeoutRestClients.withTimeouts(restClientBuilder, Duration.ofSeconds(10), CONNECTOR_CLIENT_READ_TIMEOUT)
                .baseUrl(jenkinsBaseUrl)
                .build();
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
     * Connects a GitLab project (triggers connector-gitlab's backfill), same shape as
     * {@link #connectRepo}. {@code project} is expected to be the {@code namespace/project}
     * path, not a numeric GitLab project ID — the tracked repo key here is
     * {@code "gitlab:" + project} (matching StagingEventWriter's prefix), and that only lines
     * up with what actually lands in {@code pull_request_state}/{@code workflow_run_state} when
     * the caller's input string equals the project's real {@code path_with_namespace}, which is
     * only guaranteed for the path form.
     */
    public void connectGitlabProject(String actorEmail, String project, UUID teamId, String sourceIp) {
        String repoKey = "gitlab:" + project;
        if (teamId != null) {
            teamAdminService.addRepo(teamId, repoKey);
        }
        auditLog.write(new AuditEvent(actorEmail, "GITLAB_PROJECT_CONNECT_TRIGGERED", "repo", repoKey,
                null, teamId == null ? null : "{\"teamId\":\"" + teamId + "\"}", sourceIp));
        triggers.put(repoKey, new Trigger(SyncState.IN_PROGRESS, Instant.now(), null));
        executor.submit(() -> {
            try {
                gitlabClient.post()
                        .uri("/internal/backfill?project={project}", project)
                        .retrieve()
                        .toBodilessEntity();
                triggers.put(repoKey, new Trigger(SyncState.COMPLETED, Instant.now(), null));
                log.info("GitLab project connect backfill for {} completed", repoKey);
            } catch (Exception e) {
                triggers.put(repoKey, new Trigger(SyncState.FAILED, Instant.now(), e.getMessage()));
                log.warn("GitLab project connect backfill for {} failed: {}", repoKey, e.getMessage());
            }
        });
    }

    /** GitLab's group import, same shape as {@link #connectGithubOrgTeams}. */
    public void connectGitlabGroup(String actorEmail, String group, String sourceIp) {
        auditLog.write(new AuditEvent(actorEmail, "GITLAB_GROUP_CONNECT_TRIGGERED", "gitlab_group", group,
                null, null, sourceIp));
        executor.submit(() -> {
            try {
                gitlabClient.post()
                        .uri("/internal/backfill-groups?group={group}", group)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Group import backfill for {} completed", group);
            } catch (Exception e) {
                log.warn("Group import backfill for {} failed: {}", group, e.getMessage());
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
            if (trigger != null && trigger.state() == SyncState.IN_PROGRESS
                    && trigger.at().isBefore(Instant.now().minus(STUCK_AFTER))) {
                // Belt-and-suspenders: GITHUB_CLIENT_READ_TIMEOUT above should make this
                // unreachable in practice, but a repo genuinely stuck showing "Syncing" forever
                // in the UI — with no way to tell a slow backfill from a dead one — is exactly
                // the bug this whole timeout pass exists to fix. Surface it as failed rather
                // than trust the in-memory state blindly.
                state = SyncState.FAILED;
                error = "No response after " + STUCK_AFTER.toMinutes() + " minutes — connector may be stuck; try Refresh.";
            } else if (trigger != null && trigger.state() == SyncState.IN_PROGRESS) {
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

    /**
     * Connects a Jira project (triggers connector-jira's backfill), same async trigger-and-poll
     * shape as {@link #connectRepo} — no team assignment, since no project→team mapping exists
     * yet (see class javadoc).
     */
    public void connectJiraProject(String actorEmail, String projectKey, String sourceIp) {
        auditLog.write(new AuditEvent(actorEmail, "JIRA_PROJECT_CONNECT_TRIGGERED", "jira_project", projectKey,
                null, null, sourceIp));
        jiraTriggers.put(projectKey, new Trigger(SyncState.IN_PROGRESS, Instant.now(), null));
        executor.submit(() -> {
            try {
                jiraClient.post()
                        .uri("/internal/backfill?projectKey={projectKey}", projectKey)
                        .retrieve()
                        .toBodilessEntity();
                jiraTriggers.put(projectKey, new Trigger(SyncState.COMPLETED, Instant.now(), null));
                log.info("Jira project connect backfill for {} completed", projectKey);
            } catch (Exception e) {
                jiraTriggers.put(projectKey, new Trigger(SyncState.FAILED, Instant.now(), e.getMessage()));
                log.warn("Jira project connect backfill for {} failed: {}", projectKey, e.getMessage());
            }
        });
    }

    /**
     * Removes a Jira project from every view Admin/Investment Profile/Jira Work Items actually
     * read: {@code staging.jira_issue_state} rows for that project. Same "stop showing it, not a
     * GDPR erasure" semantics as {@link #disconnectRepo} — {@code staging.raw_event} is
     * untouched, so reconnecting re-derives the same data.
     */
    public void disconnectJiraProject(String actorEmail, String projectKey, String sourceIp) {
        int rows = jdbcTemplate.update("DELETE FROM staging.jira_issue_state WHERE project_key = ?", projectKey);
        jiraTriggers.remove(projectKey);
        auditLog.write(new AuditEvent(actorEmail, "JIRA_PROJECT_DISCONNECTED", "jira_project", projectKey,
                null, "{\"issueRows\":" + rows + "}", sourceIp));
        log.info("Disconnected Jira project {}: {} issue rows removed", projectKey, rows);
    }

    /** One row per Jira project key that's either ever landed data or was just triggered. */
    public List<JiraProjectSyncStatus> listJiraProjectSyncStatus() {
        Map<String, Object[]> dbInfo = new HashMap<>();
        jdbcTemplate.query("""
                SELECT project_key, max(last_received_at) AS last_sync, count(*) AS cnt
                FROM staging.jira_issue_state
                WHERE project_key <> 'unknown'
                GROUP BY project_key
                """, rs -> {
            while (rs.next()) {
                dbInfo.put(rs.getString("project_key"),
                        new Object[]{rs.getTimestamp("last_sync"), rs.getLong("cnt")});
            }
            return null;
        });

        TreeSet<String> allProjects = new TreeSet<>();
        allProjects.addAll(dbInfo.keySet());
        allProjects.addAll(jiraTriggers.keySet());

        List<JiraProjectSyncStatus> result = new ArrayList<>();
        for (String projectKey : allProjects) {
            Object[] db = dbInfo.get(projectKey);
            Instant lastSyncAt = db == null ? null : ((Timestamp) db[0]).toInstant();
            long eventCount = db == null ? 0 : (Long) db[1];
            var resolved = resolveState(jiraTriggers.get(projectKey), lastSyncAt);
            result.add(new JiraProjectSyncStatus(projectKey, lastSyncAt, eventCount, resolved.state(), resolved.error()));
        }
        return result;
    }

    /**
     * Connects a Jenkins job (triggers connector-jenkins's backfill), same async trigger-and-poll
     * shape as {@link #connectRepo} — no team assignment, same reason as
     * {@link #connectJiraProject}.
     */
    public void connectJenkinsJob(String actorEmail, String jobName, String sourceIp) {
        auditLog.write(new AuditEvent(actorEmail, "JENKINS_JOB_CONNECT_TRIGGERED", "jenkins_job", jobName,
                null, null, sourceIp));
        jenkinsTriggers.put(jobName, new Trigger(SyncState.IN_PROGRESS, Instant.now(), null));
        executor.submit(() -> {
            try {
                jenkinsClient.post()
                        .uri("/internal/backfill?jobName={jobName}", jobName)
                        .retrieve()
                        .toBodilessEntity();
                jenkinsTriggers.put(jobName, new Trigger(SyncState.COMPLETED, Instant.now(), null));
                log.info("Jenkins job connect backfill for {} completed", jobName);
            } catch (Exception e) {
                jenkinsTriggers.put(jobName, new Trigger(SyncState.FAILED, Instant.now(), e.getMessage()));
                log.warn("Jenkins job connect backfill for {} failed: {}", jobName, e.getMessage());
            }
        });
    }

    /**
     * Removes a Jenkins job's builds from {@code staging.workflow_run_state} — the same table
     * GitHub Actions/GitLab pipelines share, so this is scoped to this job's {@code run_id}
     * prefix ({@code "jenkins:{jobName}:"}) only, never a blanket delete. Same "stop showing it"
     * semantics as {@link #disconnectRepo}.
     */
    public void disconnectJenkinsJob(String actorEmail, String jobName, String sourceIp) {
        int rows = jdbcTemplate.update(
                "DELETE FROM staging.workflow_run_state WHERE run_id LIKE ?", "jenkins:" + jobName + ":%");
        jenkinsTriggers.remove(jobName);
        auditLog.write(new AuditEvent(actorEmail, "JENKINS_JOB_DISCONNECTED", "jenkins_job", jobName,
                null, "{\"buildRows\":" + rows + "}", sourceIp));
        log.info("Disconnected Jenkins job {}: {} build rows removed", jobName, rows);
    }

    /**
     * One row per Jenkins job name that's either ever landed data or was just triggered. Jenkins
     * rows in {@code workflow_run_state} store the job name in the {@code name} column (see
     * {@code StagingEventWriter.upsertJenkinsBuildState}) — scoped to Jenkins rows via the
     * {@code run_id} prefix so this never picks up a GitHub Actions/GitLab workflow whose name
     * happens to collide with a Jenkins job name.
     */
    public List<JenkinsJobSyncStatus> listJenkinsJobSyncStatus() {
        Map<String, Object[]> dbInfo = new HashMap<>();
        jdbcTemplate.query("""
                SELECT name AS job_name, max(last_received_at) AS last_sync, count(*) AS cnt
                FROM staging.workflow_run_state
                WHERE run_id LIKE 'jenkins:%'
                GROUP BY name
                """, rs -> {
            while (rs.next()) {
                dbInfo.put(rs.getString("job_name"),
                        new Object[]{rs.getTimestamp("last_sync"), rs.getLong("cnt")});
            }
            return null;
        });

        TreeSet<String> allJobs = new TreeSet<>();
        allJobs.addAll(dbInfo.keySet());
        allJobs.addAll(jenkinsTriggers.keySet());

        List<JenkinsJobSyncStatus> result = new ArrayList<>();
        for (String jobName : allJobs) {
            Object[] db = dbInfo.get(jobName);
            Instant lastSyncAt = db == null ? null : ((Timestamp) db[0]).toInstant();
            long eventCount = db == null ? 0 : (Long) db[1];
            var resolved = resolveState(jenkinsTriggers.get(jobName), lastSyncAt);
            result.add(new JenkinsJobSyncStatus(jobName, lastSyncAt, eventCount, resolved.state(), resolved.error()));
        }
        return result;
    }

    private record ResolvedState(SyncState state, String error) {
    }

    /**
     * Shared trigger-vs-staged-data reconciliation logic — factored out of the inline version
     * that used to live only in {@link #listRepoSyncStatus()} (kept there unchanged, for the
     * repo/team-carrying case) so {@link #listJiraProjectSyncStatus()}/
     * {@link #listJenkinsJobSyncStatus()} don't duplicate the stuck-trigger/failed/completed
     * decision tree a third and fourth time.
     */
    private static ResolvedState resolveState(Trigger trigger, Instant lastSyncAt) {
        if (trigger != null && trigger.state() == SyncState.IN_PROGRESS
                && trigger.at().isBefore(Instant.now().minus(STUCK_AFTER))) {
            return new ResolvedState(SyncState.FAILED,
                    "No response after " + STUCK_AFTER.toMinutes() + " minutes — connector may be stuck; try Refresh.");
        }
        if (trigger != null && trigger.state() == SyncState.IN_PROGRESS) {
            return new ResolvedState(SyncState.IN_PROGRESS, null);
        }
        if (trigger != null && trigger.state() == SyncState.FAILED
                && (lastSyncAt == null || trigger.at().isAfter(lastSyncAt))) {
            return new ResolvedState(SyncState.FAILED, trigger.error());
        }
        if (lastSyncAt != null) {
            return new ResolvedState(SyncState.COMPLETED, null);
        }
        return new ResolvedState(SyncState.IN_PROGRESS, null);
    }
}
