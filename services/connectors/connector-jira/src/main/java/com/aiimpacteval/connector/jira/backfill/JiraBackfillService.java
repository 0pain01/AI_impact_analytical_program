package com.aiimpacteval.connector.jira.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.jira.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Backfills issues (with changelogs, feeding status-transition history for ticket lead time —
 * FR-8.2.3) for a Jira project. Idempotency via ADR-0003 sourceId scheme: re-runs re-ingest
 * only issues updated since their last staged version.
 */
@Service
public class JiraBackfillService {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(JiraBackfillService.class);
    private static final int PAGE_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;
    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int backfillDays;

    public JiraBackfillService(RestClient.Builder restClientBuilder,
                               EventPublisher publisher,
                               ObjectMapper objectMapper,
                               Clock clock,
                               @Value("${jira.base-url}") String baseUrl,
                               @Value("${jira.email}") String email,
                               @Value("${jira.api-token}") String apiToken,
                               @Value("${jira.backfill-days}") int backfillDays) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? "http://unconfigured.invalid" : baseUrl)
                .defaultHeaders(headers -> {
                    if (email != null && !email.isBlank()) {
                        headers.setBasicAuth(email, apiToken);
                    }
                })
                .defaultHeader("Accept", "application/json")
                .build();
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.backfillDays = backfillDays;
    }

    public BackfillResult backfillProject(String projectKey) {
        Instant since = Instant.now(clock).minus(Duration.ofDays(backfillDays));
        String jql = "project = " + projectKey + " AND updated >= -" + backfillDays + "d ORDER BY updated DESC";
        int published = 0;
        for (int startAt = 0; ; startAt += PAGE_SIZE) {
            final int offset = startAt;
            JsonNode page = fetchPage(() -> restClient.get()
                    .uri("/rest/api/3/search?jql={jql}&expand=changelog&maxResults={max}&startAt={start}",
                            jql, PAGE_SIZE, offset)
                    .retrieve()
                    .body(String.class));
            JsonNode issues = page == null ? null : page.get("issues");
            if (issues == null || issues.isEmpty()) {
                break;
            }
            for (JsonNode issue : issues) {
                String updated = issue.get("fields").get("updated").asText();
                publisher.publish(new EventEnvelope(
                        "jira",
                        "issue:" + issue.get("id").asText() + ":" + updated,
                        "issue.snapshot",
                        Instant.now(clock),
                        CONNECTOR_VERSION,
                        issue));
                published++;
            }
            if (issues.size() < PAGE_SIZE) {
                break;
            }
        }
        log.info("Backfill project {} complete: {} issues since {}", projectKey, published, since);
        return new BackfillResult(published, since);
    }

    private JsonNode fetchPage(Supplier<String> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                String body = call.get();
                return body == null ? null : objectMapper.readTree(body);
            } catch (Exception e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BackfillException("Jira API call failed after " + MAX_ATTEMPTS + " attempts", e);
                }
                long backoffMillis = Duration.ofSeconds(2L * attempt).toMillis();
                log.warn("Jira API call failed (attempt {}/{}), retrying in {} ms: {}",
                        attempt, MAX_ATTEMPTS, backoffMillis, e.getMessage());
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new BackfillException("Backfill interrupted", interrupted);
                }
            }
        }
    }

    public record BackfillResult(int issues, Instant since) {
    }

    public static class BackfillException extends RuntimeException {
        public BackfillException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
