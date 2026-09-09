package com.aiimpacteval.connector.gitlab.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.gitlab.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Backfills merge requests, commits, and pipelines for a project (PRD F1: 90-day default),
 * publishing each entity as a snapshot event. Idempotency via sourceId scheme from ADR-0003 —
 * re-running a backfill re-ingests only entities updated since the last run.
 *
 * <p>Unlike GitHub's pull-request list (no server-side "updated since" filter, so
 * connector-github truncates client-side against a descending sort), GitLab's merge-request
 * and pipeline endpoints accept {@code updated_after} directly, and the commits endpoint
 * accepts {@code since} — so filtering here is entirely server-side.
 *
 * <p>None of GitLab's list/get responses for merge requests, commits, or pipelines embed the
 * project's own path the way GitHub's do (a PR carries {@code base.repo.full_name}; a
 * workflow_run carries {@code repository.full_name}) — GitLab's pipeline objects in particular
 * carry no project reference at all. {@link #backfillProject} resolves the project's
 * {@code path_with_namespace} once up front and stamps it onto every published entity as
 * {@code project_path_with_namespace}, so ingestion-writer has one reliable field to key
 * {@code repo} on regardless of entity type or whether the caller passed a numeric project ID.
 */
@Service
public class GitlabBackfillService {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(GitlabBackfillService.class);
    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final EventPublisher publisher;
    private final RetryingJsonFetcher fetcher;
    private final Clock clock;
    private final int backfillDays;

    public GitlabBackfillService(RestClient.Builder restClientBuilder,
                                 EventPublisher publisher,
                                 ObjectMapper objectMapper,
                                 Clock clock,
                                 @Value("${gitlab.api-base-url}") String apiBaseUrl,
                                 @Value("${gitlab.token}") String token,
                                 @Value("${gitlab.backfill-days}") int backfillDays) {
        this.restClient = GitlabRestClients.build(restClientBuilder, apiBaseUrl, token);
        this.publisher = publisher;
        this.fetcher = new RetryingJsonFetcher(objectMapper, clock);
        this.clock = clock;
        this.backfillDays = backfillDays;
    }

    /**
     * @param project numeric project ID, or the URL-encodable {@code namespace/project} path
     *                (GitLab's {@code :id} accepts either).
     */
    public BackfillResult backfillProject(String project) {
        String projectPath = resolveProjectPath(project);
        Instant since = Instant.now(clock).minus(Duration.ofDays(backfillDays));
        int mergeRequests = backfillMergeRequests(project, projectPath, since);
        int commits = backfillCommits(project, projectPath, since);
        int pipelines = backfillPipelines(project, projectPath, since);
        log.info("Backfill {} complete: {} merge requests, {} commits, {} pipelines since {}",
                project, mergeRequests, commits, pipelines, since);
        return new BackfillResult(mergeRequests, commits, pipelines, since);
    }

    /** See class javadoc — every published entity gets stamped with this resolved path. */
    private String resolveProjectPath(String project) {
        JsonNode detail = fetcher.fetch(() -> restClient.get()
                .uri("/projects/{project}", project)
                .retrieve()
                .body(String.class));
        String resolved = detail == null ? null : textOrNull(detail, "path_with_namespace");
        // Fall back to the caller's own input (already the path form in the common case) rather
        // than publishing entities with no repo key at all if this lookup ever comes back empty.
        return resolved != null ? resolved : project;
    }

    private int backfillMergeRequests(String project, String projectPath, Instant since) {
        int published = 0;
        for (int page = 1; ; page++) {
            final int currentPage = page;
            JsonNode items = fetcher.fetch(() -> restClient.get()
                    .uri("/projects/{project}/merge_requests?state=all&updated_after={since}&per_page={size}&page={page}",
                            project, since.toString(), PAGE_SIZE, currentPage)
                    .retrieve()
                    .body(String.class));
            if (items == null || items.isEmpty()) {
                return published;
            }
            for (JsonNode mr : items) {
                stampProjectPath(mr, projectPath);
                Instant updatedAt = Instant.parse(mr.get("updated_at").asText());
                publisher.publish(new EventEnvelope(
                        "gitlab",
                        "mr:" + mr.get("id").asLong() + ":" + updatedAt,
                        "merge_request.snapshot",
                        Instant.now(clock),
                        CONNECTOR_VERSION,
                        mr));
                published++;
            }
            if (items.size() < PAGE_SIZE) {
                return published;
            }
        }
    }

    private int backfillCommits(String project, String projectPath, Instant since) {
        int published = 0;
        for (int page = 1; ; page++) {
            final int currentPage = page;
            JsonNode items = fetcher.fetch(() -> restClient.get()
                    .uri("/projects/{project}/repository/commits?since={since}&per_page={size}&page={page}",
                            project, since.toString(), PAGE_SIZE, currentPage)
                    .retrieve()
                    .body(String.class));
            if (items == null || items.isEmpty()) {
                return published;
            }
            for (JsonNode commit : items) {
                stampProjectPath(commit, projectPath);
                publisher.publish(new EventEnvelope(
                        "gitlab",
                        "commit:" + commit.get("id").asText(),
                        "commit.snapshot",
                        Instant.now(clock),
                        CONNECTOR_VERSION,
                        commit));
                published++;
            }
            if (items.size() < PAGE_SIZE) {
                return published;
            }
        }
    }

    private static void stampProjectPath(JsonNode entity, String projectPath) {
        if (entity instanceof ObjectNode objectNode) {
            objectNode.put("project_path_with_namespace", projectPath);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /**
     * GitLab CI/CD pipelines — the build/deployment events feeding DORA deployment frequency
     * and CFR (PRD F3, FR-1.3), same role as GitHub Actions workflow runs in connector-github.
     * Live updates arrive via the generic webhook ({@code pipeline} object_kind).
     */
    private int backfillPipelines(String project, String projectPath, Instant since) {
        int published = 0;
        for (int page = 1; ; page++) {
            final int currentPage = page;
            JsonNode items = fetcher.fetch(() -> restClient.get()
                    .uri("/projects/{project}/pipelines?updated_after={since}&per_page={size}&page={page}",
                            project, since.toString(), PAGE_SIZE, currentPage)
                    .retrieve()
                    .body(String.class));
            if (items == null || items.isEmpty()) {
                return published;
            }
            for (JsonNode pipeline : items) {
                stampProjectPath(pipeline, projectPath);
                publisher.publish(new EventEnvelope(
                        "gitlab",
                        "pipeline:" + pipeline.get("id").asLong() + ":" + pipeline.get("updated_at").asText(),
                        "pipeline.snapshot",
                        Instant.now(clock),
                        CONNECTOR_VERSION,
                        pipeline));
                published++;
            }
            if (items.size() < PAGE_SIZE) {
                return published;
            }
        }
    }

    public record BackfillResult(int mergeRequests, int commits, int pipelines, Instant since) {
    }
}
