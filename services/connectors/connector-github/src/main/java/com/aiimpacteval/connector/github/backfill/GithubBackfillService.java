package com.aiimpacteval.connector.github.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.github.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Backfills pull requests and commits for a repository (PRD F1: 90-day default), publishing
 * each entity as a snapshot event. Idempotency via sourceId scheme from ADR-0003 — re-running
 * a backfill re-ingests only entities updated since the last run.
 */
@Service
public class GithubBackfillService {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(GithubBackfillService.class);
    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final EventPublisher publisher;
    private final RetryingJsonFetcher fetcher;
    private final Clock clock;
    private final int backfillDays;

    public GithubBackfillService(RestClient.Builder restClientBuilder,
                                 EventPublisher publisher,
                                 ObjectMapper objectMapper,
                                 Clock clock,
                                 @Value("${github.api-base-url}") String apiBaseUrl,
                                 @Value("${github.token}") String token,
                                 @Value("${github.backfill-days}") int backfillDays) {
        this.restClient = GithubRestClients.build(restClientBuilder, apiBaseUrl, token);
        this.publisher = publisher;
        this.fetcher = new RetryingJsonFetcher(objectMapper);
        this.clock = clock;
        this.backfillDays = backfillDays;
    }

    public BackfillResult backfillRepository(String owner, String repo) {
        Instant since = Instant.now(clock).minus(Duration.ofDays(backfillDays));
        int prs = backfillPullRequests(owner, repo, since);
        int commits = backfillCommits(owner, repo, since);
        int workflowRuns = backfillWorkflowRuns(owner, repo, since);
        log.info("Backfill {}/{} complete: {} pull requests, {} commits, {} workflow runs since {}",
                owner, repo, prs, commits, workflowRuns, since);
        return new BackfillResult(prs, commits, workflowRuns, since);
    }

    private int backfillPullRequests(String owner, String repo, Instant since) {
        int published = 0;
        for (int page = 1; ; page++) {
            final int currentPage = page;
            JsonNode items = fetcher.fetch(() -> restClient.get()
                    .uri("/repos/{owner}/{repo}/pulls?state=all&sort=updated&direction=desc&per_page={size}&page={page}",
                            owner, repo, PAGE_SIZE, currentPage)
                    .retrieve()
                    .body(String.class));
            if (items == null || items.isEmpty()) {
                return published;
            }
            for (JsonNode pr : items) {
                Instant updatedAt = Instant.parse(pr.get("updated_at").asText());
                if (updatedAt.isBefore(since)) {
                    return published; // sorted by updated desc — everything after is older
                }
                publisher.publish(new EventEnvelope(
                        "github",
                        "pr:" + pr.get("id").asLong() + ":" + updatedAt,
                        "pull_request.snapshot",
                        Instant.now(clock),
                        CONNECTOR_VERSION,
                        pr));
                published++;
            }
        }
    }

    private int backfillCommits(String owner, String repo, Instant since) {
        int published = 0;
        for (int page = 1; ; page++) {
            final int currentPage = page;
            JsonNode items = fetcher.fetch(() -> restClient.get()
                    .uri("/repos/{owner}/{repo}/commits?since={since}&per_page={size}&page={page}",
                            owner, repo, since.toString(), PAGE_SIZE, currentPage)
                    .retrieve()
                    .body(String.class));
            if (items == null || items.isEmpty()) {
                return published;
            }
            for (JsonNode commit : items) {
                publisher.publish(new EventEnvelope(
                        "github",
                        "commit:" + commit.get("sha").asText(),
                        "commit.snapshot",
                        Instant.now(clock),
                        CONNECTOR_VERSION,
                        commit));
                published++;
            }
        }
    }

    /**
     * GitHub Actions runs — the build/deployment events feeding DORA deployment frequency
     * and CFR (PRD F3, FR-1.3). Live updates arrive via the generic webhook
     * ({@code workflow_run}, {@code deployment_status} event types).
     */
    private int backfillWorkflowRuns(String owner, String repo, Instant since) {
        int published = 0;
        String createdFilter = ">=" + since.toString().substring(0, 10);
        for (int page = 1; ; page++) {
            final int currentPage = page;
            JsonNode response = fetcher.fetch(() -> restClient.get()
                    .uri("/repos/{owner}/{repo}/actions/runs?created={created}&per_page={size}&page={page}",
                            owner, repo, createdFilter, PAGE_SIZE, currentPage)
                    .retrieve()
                    .body(String.class));
            JsonNode runs = response == null ? null : response.get("workflow_runs");
            if (runs == null || runs.isEmpty()) {
                return published;
            }
            for (JsonNode run : runs) {
                publisher.publish(new EventEnvelope(
                        "github",
                        "workflow_run:" + run.get("id").asLong() + ":" + run.get("updated_at").asText(),
                        "workflow_run.snapshot",
                        Instant.now(clock),
                        CONNECTOR_VERSION,
                        run));
                published++;
            }
            if (runs.size() < PAGE_SIZE) {
                return published;
            }
        }
    }

    public record BackfillResult(int pullRequests, int commits, int workflowRuns, Instant since) {
    }
}
