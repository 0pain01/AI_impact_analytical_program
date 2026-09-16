package com.aiimpacteval.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.common.events.EventTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Persists every connector event into the immutable staging store.
 *
 * <p>Idempotent by the {@code (source, source_id, event_type)} natural key (ADR-0003):
 * redeliveries and replays are no-ops. Unrecoverable messages are rejected without requeue,
 * which routes them to the DLQ — never silently dropped (FR-1.8).
 *
 * <p>Also maintains {@code staging.workflow_run_state} / {@code staging.pull_request_state} /
 * {@code staging.pull_request_review_state} / {@code staging.jira_issue_state} — typed, indexed
 * "latest known state" projections of the corresponding snapshot/webhook events. {@code
 * staging.raw_event} stays the source of truth; these projections exist purely so query
 * services don't have to re-derive "latest state per run/PR/issue" from JSONB via DISTINCT ON on
 * every request (see V5 migration for the one-time backfill of pre-existing events, and the perf
 * history that motivated this). V10 added {@code jira_issue_state} — connector-jira had been
 * publishing issue events since it was built, but nothing ever read them back out until
 * Investment Profile needed to classify git activity against Jira issue types.
 *
 * <p>{@code workflow_run_state} is fed by two independent sources now: GitHub Actions (via
 * connector-github) and Jenkins (via connector-jenkins, PRD E1-S3's alt. CI/CD source). Both
 * write into the same table/columns — the schema was already provider-agnostic (repo/run_id/
 * conclusion/name/ts, nothing GitHub-specific). The one thing that had to be handled carefully:
 * metrics-engine's DORA queries hardcode {@code conclusion = 'success'} (lowercase), but Jenkins
 * reports {@code SUCCESS}/{@code FAILURE}/{@code UNSTABLE}/{@code ABORTED} — stored verbatim,
 * every Jenkins build would have silently vanished from deployment-frequency/MTTR/CFR metrics
 * with no error. {@link #normalizeJenkinsResult} maps Jenkins' vocabulary onto the same lowercase
 * one GitHub already uses, so metrics-engine needed zero changes.
 *
 * <p>V11 added {@code staging.connector_activity} (source, last_checked_at) — a plain, always-
 * advancing upsert on every event this class processes, duplicates included. The Admin console's
 * "last sync" used to come solely from {@code MAX(raw_event.received_at)}, which only advances
 * when a NEW row actually lands — a connector that runs fine but finds nothing changed (e.g.
 * Jira re-checking issues nobody touched) correctly writes zero new rows, so that timestamp sat
 * frozen and made a healthy connector look stale. This table answers "did we hear from this
 * source at all," independent of "did anything change."
 *
 * <p>V12 added {@code staging.ai_usage_state} (PRD E9, AI-01/AI-02/AI-03) — one row per
 * {@code (source, actor_key, day)} for {@code claude_code}/{@code copilot} usage snapshots from
 * {@code connector-ai-telemetry}. Claude Code's report carries real per-day dollar cost; Copilot's
 * doesn't (flat-fee seat product), so {@link #copilotDailyCost} allocates a configurable per-seat
 * monthly price across active days only — never charged on a day with zero activity.
 *
 * <p>V13 added {@code pull_request_state.ai_assisted} (PRD E9, AI-04) — detected from each PR's
 * title/body/labels against {@link #AI_ATTRIBUTION_PATTERN}, the known trailer/signature
 * conventions AI coding assistants leave on PRs they helped author (same heuristic as the
 * "AI-assisted commits" supporting metric, applied to PRs since that's what's already in the
 * ingested payload). Lets {@code AiCostTrackQueryService} segment cycle time by AI attribution
 * instead of showing "not available yet" for AI-04/AI-05.
 *
 * <p>GitLab (connector-gitlab) feeds the same two tables Jenkins already shares with GitHub —
 * merge requests into {@code pull_request_state}, pipelines into {@code workflow_run_state} —
 * but GitLab is an independent SCM, not an alt. data source for an existing GitHub repo the way
 * Jenkins is. Its {@code repo}/{@code pr_id}/{@code run_id} values are therefore prefixed
 * ({@code "gitlab:"}) so a same-named GitLab project can never collide with (or silently merge
 * data into) a GitHub repo — same defensive-prefixing idea as Jenkins' {@code "jenkins:"}
 * run_id, just applied one level higher since GitLab genuinely is a different codebase, not the
 * same one built by a different CI tool. GitLab's pipelines carry no per-run "name" the way a
 * GitHub Actions workflow or a Jenkins job does, so {@code name} is the pipeline's git ref —
 * {@code METRICS_DEPLOY_WORKFLOW_PATTERN}/{@code METRICS_HOTFIX_WORKFLOW_PATTERN} must include
 * the deploy branch (e.g. {@code main|production}) for GitLab deployments to be detected; see
 * connector-gitlab's README.
 *
 * <p>V14 widened {@code jira_issue_state} with {@code priority}/{@code status_category}/
 * {@code reporter}/{@code labels}/{@code due_date} for the Jira Work Items dashboard — only
 * standard Jira fields present on every instance regardless of workflow configuration, never a
 * {@code customfield_XXXXX} guess (story points / epic link stay unsupported for that reason).
 */
@Component
public class StagingEventWriter {

    private static final Logger log = LoggerFactory.getLogger(StagingEventWriter.class);

    private static final String INSERT_SQL = """
            INSERT INTO staging.raw_event (source, source_id, event_type, received_at, connector_version, payload)
            VALUES (?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT ON CONSTRAINT uq_raw_event_natural_key DO NOTHING
            """;

    // Unconditional upsert (unlike INSERT_SQL above) — this must advance on every event this
    // source sends, including exact-duplicate redeliveries the raw_event insert above correctly
    // skips. It's the "did we hear from this connector at all" signal, separate from raw_event's
    // "did anything actually change" signal (see V11 migration).
    private static final String UPSERT_CONNECTOR_ACTIVITY_SQL = """
            INSERT INTO staging.connector_activity (source, last_checked_at)
            VALUES (?, ?)
            ON CONFLICT (source) DO UPDATE SET last_checked_at = EXCLUDED.last_checked_at
            WHERE EXCLUDED.last_checked_at > staging.connector_activity.last_checked_at
            """;

    // last_received_at guard: if events ever arrive out of order (retry/requeue), an older
    // snapshot can never clobber a newer one that already landed — matches the "latest by
    // received_at wins" semantics the old DISTINCT ON ... ORDER BY received_at DESC query used.
    private static final String UPSERT_WORKFLOW_RUN_SQL = """
            INSERT INTO staging.workflow_run_state (repo, run_id, conclusion, name, ts, last_received_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (repo, run_id) DO UPDATE SET
                conclusion = EXCLUDED.conclusion, name = EXCLUDED.name, ts = EXCLUDED.ts,
                last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.workflow_run_state.last_received_at
            """;

    private static final String UPSERT_PULL_REQUEST_SQL = """
            INSERT INTO staging.pull_request_state
                (repo, pr_id, number, title, author, html_url, state, requested_reviewers,
                 created_at, merged_at, ai_assisted, last_received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (repo, pr_id) DO UPDATE SET
                number = EXCLUDED.number, title = EXCLUDED.title, author = EXCLUDED.author,
                html_url = EXCLUDED.html_url, state = EXCLUDED.state,
                requested_reviewers = EXCLUDED.requested_reviewers,
                created_at = EXCLUDED.created_at, merged_at = EXCLUDED.merged_at,
                ai_assisted = EXCLUDED.ai_assisted,
                last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.pull_request_state.last_received_at
            """;

    // See V13 migration / class javadoc — mirrors metric-definitions.md's "AI-assisted commits
    // (F8)" heuristic, matched against PR title + body + label names.
    private static final Pattern AI_ATTRIBUTION_PATTERN = Pattern.compile(
            "(co-authored-by:\\s*(claude|copilot|cursor)"
                    + "|generated (with|by)\\s*(claude( code)?|copilot|cursor)"
                    + "|claude[- ]assisted|copilot[- ]assisted)",
            Pattern.CASE_INSENSITIVE);

    // Reviews can be dismissed after submission (state changes to DISMISSED), so this is a
    // guarded upsert like the others, not an insert-once.
    private static final String UPSERT_PULL_REQUEST_REVIEW_SQL = """
            INSERT INTO staging.pull_request_review_state
                (repo, pr_number, review_id, reviewer_login, state, submitted_at, last_received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (repo, review_id) DO UPDATE SET
                reviewer_login = EXCLUDED.reviewer_login, state = EXCLUDED.state,
                submitted_at = EXCLUDED.submitted_at, last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.pull_request_review_state.last_received_at
            """;

    // reopened is sticky (OR-merged, never cleared) because a webhook update event only carries
    // the changelog delta for that one change, not full history — only backfill's
    // expand=changelog scan sees the whole history at once. See wasReopened().
    private static final String UPSERT_JIRA_ISSUE_SQL = """
            INSERT INTO staging.jira_issue_state
                (issue_key, issue_id, project_key, issue_type, status, summary, assignee,
                 created_at, resolved_at, reopened, priority, status_category, reporter, labels,
                 due_date, last_received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (issue_key) DO UPDATE SET
                issue_id = EXCLUDED.issue_id, project_key = EXCLUDED.project_key,
                issue_type = EXCLUDED.issue_type, status = EXCLUDED.status,
                summary = EXCLUDED.summary, assignee = EXCLUDED.assignee,
                created_at = EXCLUDED.created_at, resolved_at = EXCLUDED.resolved_at,
                reopened = staging.jira_issue_state.reopened OR EXCLUDED.reopened,
                priority = EXCLUDED.priority, status_category = EXCLUDED.status_category,
                reporter = EXCLUDED.reporter, labels = EXCLUDED.labels,
                due_date = EXCLUDED.due_date, last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.jira_issue_state.last_received_at
            """;

    // Per (source, actor_key, day) — see V12 migration and class javadoc.
    private static final String UPSERT_AI_USAGE_SQL = """
            INSERT INTO staging.ai_usage_state
                (source, actor_key, day, sessions, loc_added, loc_removed, commits, prs, cost_usd,
                 tokens_input, tokens_output, prompts, requests, accepted_suggestions,
                 rejected_suggestions, primary_surface, last_received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (source, actor_key, day) DO UPDATE SET
                sessions = EXCLUDED.sessions, loc_added = EXCLUDED.loc_added,
                loc_removed = EXCLUDED.loc_removed, commits = EXCLUDED.commits, prs = EXCLUDED.prs,
                cost_usd = EXCLUDED.cost_usd, tokens_input = EXCLUDED.tokens_input,
                tokens_output = EXCLUDED.tokens_output, prompts = EXCLUDED.prompts,
                requests = EXCLUDED.requests, accepted_suggestions = EXCLUDED.accepted_suggestions,
                rejected_suggestions = EXCLUDED.rejected_suggestions,
                primary_surface = EXCLUDED.primary_surface, last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.ai_usage_state.last_received_at
            """;

    private static final Set<String> WORKFLOW_RUN_EVENT_TYPES = Set.of("workflow_run", "workflow_run.snapshot");
    private static final Set<String> PULL_REQUEST_EVENT_TYPES = Set.of("pull_request", "pull_request.snapshot");
    private static final Set<String> PULL_REQUEST_REVIEW_EVENT_TYPES =
            Set.of("pull_request_review", "pull_request_review.snapshot");
    // jira:issue_deleted is deliberately not in this set — no reliable fields left to project,
    // and dropping the row on delete isn't worth the added complexity for a rare event.
    private static final Set<String> JIRA_ISSUE_EVENT_TYPES =
            Set.of("issue.snapshot", "jira:issue_created", "jira:issue_updated");
    private static final Set<String> TERMINAL_STATUS_NAMES = Set.of("done", "closed", "resolved");
    private static final Set<String> JENKINS_BUILD_EVENT_TYPES = Set.of("build.snapshot");
    private static final String JENKINS_GIT_BUILD_DATA_CLASS = "hudson.plugins.git.util.BuildData";
    private static final Set<String> GITLAB_MERGE_REQUEST_EVENT_TYPES =
            Set.of("merge_request", "merge_request.snapshot");
    private static final Set<String> GITLAB_PIPELINE_EVENT_TYPES = Set.of("pipeline", "pipeline.snapshot");
    private static final Set<String> GITLAB_APPROVAL_EVENT_TYPES = Set.of("merge_request_approval.snapshot");
    private static final Set<String> AI_USAGE_EVENT_TYPES = Set.of("usage.snapshot");
    private static final int DAYS_PER_MONTH = 30;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BigDecimal copilotMonthlySeatCostUsd;

    public StagingEventWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                              @Value("${ai-cost.copilot.monthly-seat-cost-usd}") BigDecimal copilotMonthlySeatCostUsd) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.copilotMonthlySeatCostUsd = copilotMonthlySeatCostUsd;
    }

    @RabbitListener(queues = EventTopology.STAGING_QUEUE)
    public void onEvent(EventEnvelope envelope) {
        write(envelope);
    }

    /** @return true if a new row was written, false if it was a duplicate (idempotent skip). */
    public boolean write(EventEnvelope envelope) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope.payload());
        } catch (JsonProcessingException e) {
            // Malformed beyond repair — reject without requeue so it lands in the DLQ.
            throw new AmqpRejectAndDontRequeueException("Unserializable payload for " + envelope.sourceId(), e);
        }

        jdbcTemplate.update(UPSERT_CONNECTOR_ACTIVITY_SQL, envelope.source(), Timestamp.from(envelope.receivedAt()));

        int inserted = jdbcTemplate.update(INSERT_SQL,
                envelope.source(),
                envelope.sourceId(),
                envelope.eventType(),
                Timestamp.from(envelope.receivedAt()),
                envelope.connectorVersion(),
                payloadJson);

        if (inserted == 0) {
            log.debug("Duplicate event skipped: {}/{}/{}",
                    envelope.source(), envelope.sourceId(), envelope.eventType());
            return false;
        }

        if ("github".equals(envelope.source())) {
            if (WORKFLOW_RUN_EVENT_TYPES.contains(envelope.eventType())) {
                upsertWorkflowRunState(envelope);
            } else if (PULL_REQUEST_EVENT_TYPES.contains(envelope.eventType())) {
                upsertPullRequestState(envelope);
            } else if (PULL_REQUEST_REVIEW_EVENT_TYPES.contains(envelope.eventType())) {
                upsertPullRequestReviewState(envelope);
            }
        } else if ("jira".equals(envelope.source()) && JIRA_ISSUE_EVENT_TYPES.contains(envelope.eventType())) {
            upsertJiraIssueState(envelope);
        } else if ("jenkins".equals(envelope.source()) && JENKINS_BUILD_EVENT_TYPES.contains(envelope.eventType())) {
            upsertJenkinsBuildState(envelope);
        } else if ("gitlab".equals(envelope.source())) {
            if (GITLAB_MERGE_REQUEST_EVENT_TYPES.contains(envelope.eventType())) {
                upsertGitlabMergeRequestState(envelope);
            } else if (GITLAB_PIPELINE_EVENT_TYPES.contains(envelope.eventType())) {
                upsertGitlabPipelineState(envelope);
            } else if (GITLAB_APPROVAL_EVENT_TYPES.contains(envelope.eventType())) {
                upsertGitlabApprovalState(envelope);
            }
        } else if (("claude_code".equals(envelope.source()) || "copilot".equals(envelope.source()))
                && AI_USAGE_EVENT_TYPES.contains(envelope.eventType())) {
            upsertAiUsageState(envelope);
        }
        return true;
    }

    private void upsertWorkflowRunState(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        JsonNode run = payload.has("workflow_run") ? payload.get("workflow_run") : payload;

        String runId = textOrNull(run, "id");
        if (runId == null) {
            return; // nothing to key the projection on — skip rather than guess
        }
        String repo = firstNonBlank(
                textAtPath(run, "repository", "full_name"),
                textAtPath(payload, "repository", "full_name"),
                "unknown");
        String conclusion = textOrNull(run, "conclusion");
        String name = textOrNull(run, "name");
        Instant ts = instantOrNull(textOrNull(run, "updated_at"));

        jdbcTemplate.update(UPSERT_WORKFLOW_RUN_SQL,
                repo, runId, conclusion, name,
                ts == null ? null : Timestamp.from(ts),
                Timestamp.from(envelope.receivedAt()));
    }

    private void upsertPullRequestState(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        JsonNode pr = payload.has("pull_request") ? payload.get("pull_request") : payload;

        String prId = textOrNull(pr, "id");
        if (prId == null) {
            return;
        }
        String repo = firstNonBlank(textAtPath(pr, "base", "repo", "full_name"), "unknown");
        Long number = longOrNull(textOrNull(pr, "number"));
        String title = textOrNull(pr, "title");
        String author = textAtPath(pr, "user", "login");
        String htmlUrl = textOrNull(pr, "html_url");
        String state = textOrNull(pr, "state");
        String[] requestedReviewers = extractLogins(pr.get("requested_reviewers"));
        Instant createdAt = instantOrNull(textOrNull(pr, "created_at"));
        Instant mergedAt = instantOrNull(textOrNull(pr, "merged_at"));
        boolean aiAssisted = detectAiAssisted(pr);

        // Plain jdbcTemplate.update(...) can't portably bind a text[] parameter, so this one
        // needs a PreparedStatementCreator to call Connection.createArrayOf ourselves.
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(UPSERT_PULL_REQUEST_SQL);
            ps.setString(1, repo);
            ps.setString(2, prId);
            if (number == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, number);
            }
            ps.setString(4, title);
            ps.setString(5, author);
            ps.setString(6, htmlUrl);
            ps.setString(7, state);
            ps.setArray(8, con.createArrayOf("text", requestedReviewers));
            ps.setTimestamp(9, createdAt == null ? null : Timestamp.from(createdAt));
            ps.setTimestamp(10, mergedAt == null ? null : Timestamp.from(mergedAt));
            ps.setBoolean(11, aiAssisted);
            ps.setTimestamp(12, Timestamp.from(envelope.receivedAt()));
            return ps;
        });
    }

    private static boolean detectAiAssisted(JsonNode pr) {
        String title = textOrNull(pr, "title");
        String body = textOrNull(pr, "body");
        StringBuilder text = new StringBuilder()
                .append(title == null ? "" : title).append(' ')
                .append(body == null ? "" : body);
        JsonNode labels = pr.get("labels");
        if (labels != null && labels.isArray()) {
            for (JsonNode label : labels) {
                String name = textOrNull(label, "name");
                if (name != null) {
                    text.append(' ').append(name);
                }
            }
        }
        return AI_ATTRIBUTION_PATTERN.matcher(text).find();
    }

    private void upsertPullRequestReviewState(EventEnvelope envelope) {
        JsonNode review = envelope.payload();

        String reviewId = textOrNull(review, "id");
        String prUrl = textOrNull(review, "pull_request_url");
        if (reviewId == null || prUrl == null) {
            return;
        }
        RepoAndPrNumber location = parsePullRequestUrl(prUrl);
        if (location == null) {
            log.warn("Could not parse repo/PR number from pull_request_url '{}' — skipping review {}",
                    prUrl, reviewId);
            return;
        }
        String reviewerLogin = textAtPath(review, "user", "login");
        String state = textOrNull(review, "state");
        Instant submittedAt = instantOrNull(textOrNull(review, "submitted_at"));

        jdbcTemplate.update(UPSERT_PULL_REQUEST_REVIEW_SQL,
                location.repo(), location.prNumber(), reviewId, reviewerLogin, state,
                submittedAt == null ? null : Timestamp.from(submittedAt),
                Timestamp.from(envelope.receivedAt()));
    }

    private void upsertJiraIssueState(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        // Backfill (issue.snapshot): payload IS the issue object. Webhook (jira:issue_created/
        // _updated): payload wraps it under "issue", with "changelog" as a sibling, not nested.
        JsonNode issue = payload.has("issue") ? payload.get("issue") : payload;
        JsonNode fields = issue.get("fields");
        String issueKey = textOrNull(issue, "key");
        String issueId = textOrNull(issue, "id");
        if (issueKey == null || issueId == null || fields == null) {
            return;
        }
        String projectKey = firstNonBlank(textAtPath(fields, "project", "key"), "unknown");
        String issueType = textAtPath(fields, "issuetype", "name");
        String status = textAtPath(fields, "status", "name");
        String summary = textOrNull(fields, "summary");
        String assignee = textAtPath(fields, "assignee", "displayName");
        Instant createdAt = jiraInstantOrNull(textOrNull(fields, "created"));
        Instant resolvedAt = jiraInstantOrNull(textOrNull(fields, "resolutiondate"));
        String priority = textAtPath(fields, "priority", "name");
        String statusCategory = textAtPath(fields, "status", "statusCategory", "key");
        String reporter = textAtPath(fields, "reporter", "displayName");
        String[] labels = extractPlainStrings(fields.get("labels"));
        LocalDate dueDate = jiraDateOrNull(textOrNull(fields, "duedate"));

        JsonNode changelog = payload.has("changelog") ? payload.get("changelog") : issue.get("changelog");
        boolean reopened = wasReopened(changelog);

        // Plain jdbcTemplate.update(...) can't portably bind a text[] parameter (labels), so this
        // needs a PreparedStatementCreator — same reason upsertPullRequestState does.
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(UPSERT_JIRA_ISSUE_SQL);
            ps.setString(1, issueKey);
            ps.setString(2, issueId);
            ps.setString(3, projectKey);
            ps.setString(4, issueType);
            ps.setString(5, status);
            ps.setString(6, summary);
            ps.setString(7, assignee);
            ps.setTimestamp(8, createdAt == null ? null : Timestamp.from(createdAt));
            ps.setTimestamp(9, resolvedAt == null ? null : Timestamp.from(resolvedAt));
            ps.setBoolean(10, reopened);
            ps.setString(11, priority);
            ps.setString(12, statusCategory);
            ps.setString(13, reporter);
            ps.setArray(14, con.createArrayOf("text", labels));
            ps.setDate(15, dueDate == null ? null : Date.valueOf(dueDate));
            ps.setTimestamp(16, Timestamp.from(envelope.receivedAt()));
            return ps;
        });
    }

    /**
     * Heuristic, not authoritative (see V10 migration comment): flags a status transition away
     * from one of Jira's default terminal status names (Done/Closed/Resolved). Projects on
     * custom workflows with differently-named terminal statuses won't be caught — this
     * undercounts rework rather than overcounting it, which is the safer direction to be wrong
     * in for a metric people will make decisions from.
     */
    private static boolean wasReopened(JsonNode changelog) {
        if (changelog == null) {
            return false;
        }
        JsonNode histories = changelog.get("histories");
        if (histories != null && histories.isArray()) {
            // Backfill's expand=changelog: full history, one entry per past change.
            for (JsonNode history : histories) {
                if (statusItemsShowReopen(history.get("items"))) {
                    return true;
                }
            }
            return false;
        }
        // Webhook update delta: a single changelog entry shaped {"items": [...]}, no wrapper.
        return statusItemsShowReopen(changelog.get("items"));
    }

    private static boolean statusItemsShowReopen(JsonNode items) {
        if (items == null || !items.isArray()) {
            return false;
        }
        for (JsonNode item : items) {
            if ("status".equals(textOrNull(item, "field"))) {
                String from = textOrNull(item, "fromString");
                if (from != null && TERMINAL_STATUS_NAMES.contains(from.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void upsertJenkinsBuildState(EventEnvelope envelope) {
        JsonNode build = envelope.payload();

        String buildNumber = textOrNull(build, "number");
        String jobName = textOrNull(build, "job_name");
        if (buildNumber == null || jobName == null) {
            return;
        }
        // Build numbers only reset per-job in Jenkins, not globally — two different jobs could
        // both have a "build #5". Namespacing by job here keeps (repo, run_id) unique.
        String runId = "jenkins:" + jobName + ":" + buildNumber;

        String repo = extractJenkinsRepo(build.get("actions"));
        String conclusion = normalizeJenkinsResult(textOrNull(build, "result"));
        Long timestampMillis = longOrNull(build, "timestamp");
        Instant ts = timestampMillis == null ? null : Instant.ofEpochMilli(timestampMillis);

        jdbcTemplate.update(UPSERT_WORKFLOW_RUN_SQL,
                repo == null ? "unknown" : repo,
                runId,
                conclusion,
                jobName,
                ts == null ? null : Timestamp.from(ts),
                Timestamp.from(envelope.receivedAt()));
    }

    /**
     * Jenkins doesn't put the git repo on the build object directly — it's inside a
     * {@code hudson.plugins.git.util.BuildData} entry in the (otherwise mostly-empty-object)
     * {@code actions} array. Verified against a real local Jenkins instance, not assumed from
     * docs — the shape genuinely is this scattered.
     */
    private static String extractJenkinsRepo(JsonNode actions) {
        if (actions == null || !actions.isArray()) {
            return null;
        }
        for (JsonNode action : actions) {
            if (!JENKINS_GIT_BUILD_DATA_CLASS.equals(textOrNull(action, "_class"))) {
                continue;
            }
            JsonNode remoteUrls = action.get("remoteUrls");
            if (remoteUrls != null && remoteUrls.isArray() && !remoteUrls.isEmpty()) {
                return normalizeGitUrl(remoteUrls.get(0).asText());
            }
        }
        return null;
    }

    // "https://github.com/0pain01/AI_impact_analytical_program.git" -> "0pain01/AI_impact_analytical_program"
    // Falls back to returning the trimmed URL as-is for non-GitHub git hosts rather than
    // dropping the data — better an unfamiliar-looking repo value than a silently missing one.
    private static String normalizeGitUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.endsWith(".git")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        int idx = trimmed.indexOf("github.com/");
        return idx >= 0 ? trimmed.substring(idx + "github.com/".length()) : trimmed;
    }

    // metrics-engine's DORA queries hardcode `conclusion = 'success'` (lowercase) — Jenkins
    // reports SUCCESS/FAILURE/UNSTABLE/ABORTED. Without this mapping, every Jenkins build would
    // silently never match those queries; see class javadoc.
    private static String normalizeJenkinsResult(String rawResult) {
        if (rawResult == null) {
            return null; // still building — no result yet, same as an in-progress GitHub run
        }
        return switch (rawResult) {
            case "SUCCESS" -> "success";
            case "FAILURE", "UNSTABLE" -> "failure";
            case "ABORTED" -> "cancelled";
            default -> rawResult.toLowerCase(Locale.ROOT);
        };
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asLong();
    }

    /**
     * GitLab merge requests → the same {@code pull_request_state} table GitHub PRs use. Webhook
     * ({@code merge_request} object_kind) nests the entity under {@code object_attributes} with
     * a sibling top-level {@code project.path_with_namespace}; backfill ({@code
     * merge_request.snapshot}) payload IS the MR object, carrying GitlabBackfillService's
     * injected {@code project_path_with_namespace} instead (see its javadoc — GitLab's MR API
     * doesn't reliably embed the project path itself the way GitHub's does).
     */
    private void upsertGitlabMergeRequestState(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        JsonNode mr = payload.has("object_attributes") ? payload.get("object_attributes") : payload;

        String mrId = textOrNull(mr, "id");
        if (mrId == null) {
            return;
        }
        // See class javadoc: GitLab is an independent SCM, not an alt. source for a GitHub repo
        // — prefixed so it can never collide with a same-named GitHub repo/PR.
        String repo = "gitlab:" + firstNonBlank(
                textAtPath(payload, "project", "path_with_namespace"),
                textOrNull(mr, "project_path_with_namespace"),
                "unknown");
        Long number = longOrNull(textOrNull(mr, "iid"));
        String title = textOrNull(mr, "title");
        // object_attributes historically carries only author_id (a bare number) on the webhook
        // path, not a resolvable username — the top-level "user" (who triggered the webhook) is
        // the best available fallback, correct for the common "opened"/"updated by author" case.
        // Backfill's MR object always has the full author sub-object.
        String author = firstNonBlank(textAtPath(mr, "author", "username"), textAtPath(payload, "user", "username"));
        String htmlUrl = firstNonBlank(textOrNull(mr, "url"), textOrNull(mr, "web_url"));
        String state = normalizeGitlabMrState(textOrNull(mr, "state"));
        String[] requestedReviewers = extractGitlabReviewerUsernames(mr.get("reviewers"));
        Instant createdAt = instantOrNull(textOrNull(mr, "created_at"));
        Instant mergedAt = instantOrNull(textOrNull(mr, "merged_at"));
        boolean aiAssisted = detectAiAssistedGitlab(mr);

        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(UPSERT_PULL_REQUEST_SQL);
            ps.setString(1, repo);
            ps.setString(2, "gitlab:" + mrId);
            if (number == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, number);
            }
            ps.setString(4, title);
            ps.setString(5, author);
            ps.setString(6, htmlUrl);
            ps.setString(7, state);
            ps.setArray(8, con.createArrayOf("text", requestedReviewers));
            ps.setTimestamp(9, createdAt == null ? null : Timestamp.from(createdAt));
            ps.setTimestamp(10, mergedAt == null ? null : Timestamp.from(mergedAt));
            ps.setBoolean(11, aiAssisted);
            ps.setTimestamp(12, Timestamp.from(envelope.receivedAt()));
            return ps;
        });
    }

    /**
     * GitLab merge-request approvals → the same {@code pull_request_review_state} table
     * GitHub's PR reviews use (Code Review tab's cycle-stage breakdown and reviewer-load both
     * read this table regardless of source). GitLab's approval model has no equivalent to
     * GitHub's "changes requested"/"commented" review states — only "approved, by whom, when" —
     * so {@code state} is always {@code APPROVED} here; connector-gitlab's
     * {@code backfillMergeRequestApprovals} javadoc explains why nothing broader is guessed.
     *
     * <p>{@code review_id} has no natural GitLab equivalent (unlike a GitHub review's own numeric
     * id) — synthesized as {@code "gitlab:" + mergeRequestId + ":" + username}, unique per
     * approver per MR and stable across re-backfills (ADR-0003).
     */
    private void upsertGitlabApprovalState(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        JsonNode user = payload.get("user");
        String username = user == null ? null : textOrNull(user, "username");
        String approvedAt = textOrNull(payload, "approved_at");
        Long mrIid = longOrNull(textOrNull(payload, "merge_request_iid"));
        if (username == null || approvedAt == null || mrIid == null) {
            return;
        }
        String repo = "gitlab:" + firstNonBlank(
                textOrNull(payload, "project_path_with_namespace"), "unknown");
        // sourceId ("mr_approval:{id}:{username}") already carries the MR's internal id, so a
        // per-user review_id keyed on the same MR + username stays stable across re-backfills.
        String reviewId = "gitlab:" + envelope.sourceId();
        Instant submittedAt = instantOrNull(approvedAt);

        jdbcTemplate.update(UPSERT_PULL_REQUEST_REVIEW_SQL,
                repo, mrIid, reviewId, username, "APPROVED",
                submittedAt == null ? null : Timestamp.from(submittedAt),
                Timestamp.from(envelope.receivedAt()));
    }

    // GitHub's PR state is a plain open/closed pair (a merged PR still reports state=closed;
    // merged_at is the authoritative merge signal metrics-engine actually queries). GitLab adds
    // "merged"/"locked" as their own state values — normalized onto GitHub's vocabulary so
    // anything that does read this column (e.g. an "open PRs" list) behaves the same across
    // both connectors; merged_at, not this column, is what DORA/PR-velocity queries key on.
    private static String normalizeGitlabMrState(String rawState) {
        if (rawState == null) {
            return null;
        }
        return switch (rawState) {
            case "opened" -> "open";
            case "merged", "locked" -> "closed";
            default -> rawState; // "closed" already matches GitHub's vocabulary
        };
    }

    // Backfill's /merge_requests response returns reviewers as full user objects (username
    // present); the webhook's object_attributes.reviewer_ids (where present at all — added in
    // GitLab 15.3) is bare numeric IDs with no username to resolve without an extra API call
    // connectors don't make (ADR-0002) — those simply contribute nothing here, same as a PR with
    // no requested reviewers. A later backfill/refresh republishes the fuller shape.
    private static String[] extractGitlabReviewerUsernames(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return new String[0];
        }
        List<String> usernames = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            String username = textOrNull(item, "username");
            if (username != null) {
                usernames.add(username);
            }
        }
        return usernames.toArray(new String[0]);
    }

    // Same heuristic as detectAiAssisted, adapted for GitLab's field names: "description" not
    // "body", and labels as plain strings from the REST API (webhook object_attributes has used
    // {"title": "..."} label objects in some GitLab versions — handled defensively).
    private static boolean detectAiAssistedGitlab(JsonNode mr) {
        String title = textOrNull(mr, "title");
        String description = firstNonBlank(textOrNull(mr, "description"), textOrNull(mr, "body"));
        StringBuilder text = new StringBuilder()
                .append(title == null ? "" : title).append(' ')
                .append(description == null ? "" : description);
        JsonNode labels = mr.get("labels");
        if (labels != null && labels.isArray()) {
            for (JsonNode label : labels) {
                String name = label.isTextual() ? label.asText() : textOrNull(label, "title");
                if (name != null) {
                    text.append(' ').append(name);
                }
            }
        }
        return AI_ATTRIBUTION_PATTERN.matcher(text).find();
    }

    /**
     * GitLab pipelines → the same {@code workflow_run_state} table GitHub Actions/Jenkins
     * share. Webhook ({@code pipeline} object_kind) nests the entity under
     * {@code object_attributes} with a sibling top-level {@code project.path_with_namespace};
     * backfill ({@code pipeline.snapshot}) payload IS the pipeline object, carrying
     * GitlabBackfillService's injected {@code project_path_with_namespace}.
     */
    private void upsertGitlabPipelineState(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        JsonNode pipeline = payload.has("object_attributes") ? payload.get("object_attributes") : payload;

        String runId = textOrNull(pipeline, "id");
        if (runId == null) {
            return;
        }
        String repo = "gitlab:" + firstNonBlank(
                textAtPath(payload, "project", "path_with_namespace"),
                textOrNull(pipeline, "project_path_with_namespace"),
                "unknown");
        // GitLab pipelines have no per-run "name" the way a GitHub Actions workflow or a Jenkins
        // job does — the closest available field is the git ref the pipeline ran on. See class
        // javadoc: the deploy/hotfix pattern env vars must include the deploy branch for GitLab
        // deployments to be detected by name-pattern matching.
        String name = textOrNull(pipeline, "ref");
        String conclusion = normalizeGitlabPipelineStatus(textOrNull(pipeline, "status"));
        Instant ts = instantOrNull(firstNonBlank(
                textOrNull(pipeline, "finished_at"), textOrNull(pipeline, "updated_at")));

        jdbcTemplate.update(UPSERT_WORKFLOW_RUN_SQL,
                repo, "gitlab:" + runId, conclusion, name,
                ts == null ? null : Timestamp.from(ts),
                Timestamp.from(envelope.receivedAt()));
    }

    // metrics-engine's DORA queries hardcode conclusion = 'success' (lowercase) — GitLab reports
    // lowercase statuses already, but spells failure/cancellation differently
    // (failed/canceled vs. GitHub's failure/cancelled); normalized onto the same vocabulary
    // Jenkins' normalizeJenkinsResult already established. running/pending/skipped/manual/
    // created pass through unchanged — nothing currently queries for those values.
    private static String normalizeGitlabPipelineStatus(String rawStatus) {
        if (rawStatus == null) {
            return null;
        }
        return switch (rawStatus) {
            case "success" -> "success";
            case "failed" -> "failure";
            case "canceled" -> "cancelled";
            default -> rawStatus;
        };
    }

    private void upsertAiUsageState(EventEnvelope envelope) {
        if ("claude_code".equals(envelope.source())) {
            upsertClaudeCodeUsage(envelope);
        } else {
            upsertCopilotUsage(envelope);
        }
    }

    /**
     * Claude Code's usage report shape (verified against a real sample export — see
     * connector-ai-telemetry's {@code ClaudeCodeUsageBackfillService} javadoc): {@code
     * core_metrics} for sessions/LOC/commits/PRs, {@code model_breakdown[]} for tokens and real
     * per-day dollar cost (summed across every model the user touched that day), {@code
     * tool_actions.*} for accept/reject counts across edit/multi-edit/write/notebook-edit tools.
     */
    private void upsertClaudeCodeUsage(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        String email = textAtPath(payload, "actor", "email_address");
        String dateRaw = textOrNull(payload, "date");
        if (email == null || dateRaw == null) {
            return;
        }
        String day = dateRaw.length() >= 10 ? dateRaw.substring(0, 10) : dateRaw;

        JsonNode coreMetrics = payload.get("core_metrics");
        Integer sessions = intOrNull(coreMetrics, "num_sessions");
        JsonNode loc = coreMetrics == null ? null : coreMetrics.get("lines_of_code");
        Integer locAdded = intOrNull(loc, "added");
        Integer locRemoved = intOrNull(loc, "removed");
        Integer commits = intOrNull(coreMetrics, "commits_by_claude_code");
        Integer prs = intOrNull(coreMetrics, "pull_requests_by_claude_code");

        long tokensInput = 0;
        long tokensOutput = 0;
        BigDecimal cost = BigDecimal.ZERO;
        JsonNode modelBreakdown = payload.get("model_breakdown");
        if (modelBreakdown != null && modelBreakdown.isArray()) {
            for (JsonNode model : modelBreakdown) {
                JsonNode tokens = model.get("tokens");
                tokensInput += longOrZero(tokens, "input");
                tokensOutput += longOrZero(tokens, "output");
                JsonNode estimatedCost = model.get("estimated_cost");
                if (estimatedCost != null && estimatedCost.has("amount") && !estimatedCost.get("amount").isNull()) {
                    cost = cost.add(BigDecimal.valueOf(estimatedCost.get("amount").asDouble()));
                }
            }
        }

        int accepted = 0;
        int rejected = 0;
        JsonNode toolActions = payload.get("tool_actions");
        if (toolActions != null) {
            Iterator<Map.Entry<String, JsonNode>> fields = toolActions.fields();
            while (fields.hasNext()) {
                JsonNode action = fields.next().getValue();
                accepted += intOrZero(action, "accepted");
                rejected += intOrZero(action, "rejected");
            }
        }

        String surface = textOrNull(payload, "terminal_type");

        jdbcTemplate.update(UPSERT_AI_USAGE_SQL,
                "claude_code", email, Date.valueOf(day), sessions, locAdded, locRemoved, commits, prs,
                cost, tokensInput, tokensOutput, null, null, accepted, rejected, surface,
                Timestamp.from(envelope.receivedAt()));
    }

    /**
     * Copilot's usage export shape (verified against a real sample export — see
     * connector-ai-telemetry's {@code CopilotUsageBackfillService} javadoc): top-level
     * {@code loc_added_sum}/{@code loc_deleted_sum}/{@code code_acceptance_activity_count},
     * {@code totals_by_cli} for sessions/prompts/requests/tokens, {@code totals_by_ide[0]} as the
     * day's primary surface. No per-request dollar cost exists — {@link #copilotDailyCost}
     * allocates the configured flat-fee seat price across active days only.
     */
    private void upsertCopilotUsage(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        String login = textOrNull(payload, "user_login");
        String day = textOrNull(payload, "day");
        if (login == null || day == null) {
            return;
        }

        Integer locAdded = intOrNull(payload, "loc_added_sum");
        Integer locRemoved = intOrNull(payload, "loc_deleted_sum");
        Integer accepted = intOrNull(payload, "code_acceptance_activity_count");

        JsonNode cli = payload.get("totals_by_cli");
        Integer sessions = intOrNull(cli, "session_count");
        Integer prompts = intOrNull(cli, "prompt_count");
        Integer requests = intOrNull(cli, "request_count");
        JsonNode tokenUsage = cli == null ? null : cli.get("token_usage");
        Long tokensInput = tokenUsage == null ? null : longOrNull(tokenUsage, "prompt_tokens_sum");
        Long tokensOutput = tokenUsage == null ? null : longOrNull(tokenUsage, "output_tokens_sum");

        String surface = null;
        JsonNode ides = payload.get("totals_by_ide");
        if (ides != null && ides.isArray() && !ides.isEmpty()) {
            surface = textOrNull(ides.get(0), "ide");
        }

        BigDecimal cost = copilotDailyCost(sessions, prompts, requests);

        jdbcTemplate.update(UPSERT_AI_USAGE_SQL,
                "copilot", login, Date.valueOf(day), sessions, locAdded, locRemoved, null, null,
                cost, tokensInput, tokensOutput, prompts, requests, accepted, null, surface,
                Timestamp.from(envelope.receivedAt()));
    }

    // AI-01's documented edge case: "un-metered/flat-fee tools use allocated seat cost" — never
    // a blind charge on every calendar day, only ones with observed activity.
    private BigDecimal copilotDailyCost(Integer sessions, Integer prompts, Integer requests) {
        boolean active = (sessions != null && sessions > 0) || (prompts != null && prompts > 0)
                || (requests != null && requests > 0);
        if (!active) {
            return BigDecimal.ZERO;
        }
        return copilotMonthlySeatCostUsd.divide(BigDecimal.valueOf(DAYS_PER_MONTH), 2, RoundingMode.HALF_UP);
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asInt();
    }

    private static int intOrZero(JsonNode node, String field) {
        Integer v = intOrNull(node, field);
        return v == null ? 0 : v;
    }

    private static long longOrZero(JsonNode node, String field) {
        if (node == null) {
            return 0L;
        }
        Long v = longOrNull(node, field);
        return v == null ? 0L : v;
    }

    private record RepoAndPrNumber(String repo, long prNumber) {
    }

    // e.g. "https://api.github.com/repos/expressjs/express/pulls/7369" -> ("expressjs/express", 7369)
    private static RepoAndPrNumber parsePullRequestUrl(String url) {
        int reposIdx = url.indexOf("/repos/");
        int pullsIdx = url.indexOf("/pulls/");
        if (reposIdx < 0 || pullsIdx < 0 || pullsIdx <= reposIdx) {
            return null;
        }
        String repo = url.substring(reposIdx + "/repos/".length(), pullsIdx);
        String numberStr = url.substring(pullsIdx + "/pulls/".length());
        try {
            return new RepoAndPrNumber(repo, Long.parseLong(numberStr));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String textAtPath(JsonNode node, String... path) {
        JsonNode cur = node;
        for (String p : path) {
            if (cur == null || cur.isNull()) {
                return null;
            }
            cur = cur.get(p);
        }
        return cur == null || cur.isNull() ? null : cur.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static Instant instantOrNull(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            log.warn("Unparseable timestamp '{}' — leaving null", iso);
            return null;
        }
    }

    // Jira sends "2024-01-15T10:30:00.000+0000" (no colon in the offset) rather than GitHub's
    // proper ISO-8601 "...Z" — Instant.parse rejects that format outright, so this tries it
    // first (harmless if Jira ever does send a real 'Z'/colon offset) and falls back to Jira's
    // actual format rather than silently losing the date on every single issue.
    private static final DateTimeFormatter JIRA_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private static Instant jiraInstantOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception isoFailed) {
            try {
                return OffsetDateTime.parse(raw, JIRA_TIMESTAMP_FORMAT).toInstant();
            } catch (Exception e) {
                log.warn("Unparseable Jira timestamp '{}' — leaving null", raw);
                return null;
            }
        }
    }

    private static Long longOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // fields.labels is a Jira standard field: a bare array of strings (unlike requested_reviewers'
    // array of objects), so this only needs to filter out nulls, not project a sub-field.
    private static String[] extractPlainStrings(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return new String[0];
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            if (item != null && !item.isNull()) {
                values.add(item.asText());
            }
        }
        return values.toArray(new String[0]);
    }

    // fields.duedate is a bare "yyyy-MM-dd" date, unlike Jira's other timestamp fields
    // (created/resolutiondate/updated) which carry a full offset-datetime — see jiraInstantOrNull.
    private static LocalDate jiraDateOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (Exception e) {
            log.warn("Unparseable Jira date '{}' — leaving null", raw);
            return null;
        }
    }

    // requested_reviewers is an array of GitHub user objects — we only need their logins.
    private static String[] extractLogins(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return new String[0];
        }
        List<String> logins = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            String login = textOrNull(item, "login");
            if (login != null) {
                logins.add(login);
            }
        }
        return logins.toArray(new String[0]);
    }
}
