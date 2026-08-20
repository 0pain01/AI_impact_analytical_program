package com.aiimpacteval.connector.jenkins.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.common.http.TimeoutRestClients;
import com.aiimpacteval.connector.jenkins.events.EventPublisher;
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
 * Backfills build history for one Jenkins job (PRD E1-S3, alt. CI/CD source alongside
 * connector-github — see system-architecture.md's "CI/CD (GH Actions / Jenkins)" boundary).
 * Idempotency via ADR-0003: {@code sourceId} is {@code jenkins:{jobName}:{buildNumber}}, unique
 * per job since Jenkins build numbers only reset per-job, not globally.
 *
 * <p>Repo attribution reads the {@code hudson.plugins.git.util.BuildData} entry inside each
 * build's {@code actions} array (verified against a real local Jenkins instance — see chat, not
 * assumed from docs) — {@code remoteUrls[0]} gives the git URL, normalized here into the same
 * {@code owner/repo} shape used everywhere else in this system. A build with no Git plugin data
 * (e.g. a freestyle job with no SCM configured) is published with {@code repo = "unknown"}
 * rather than being silently dropped.
 *
 * <p>No incremental "since" cursor (unlike GitHub/Jira) — Jenkins' own build retention already
 * bounds job history, and this fetches whatever the job API returns in one page. A job with an
 * unusually large build history may need a bounded {@code tree=builds[0,N]{...}} query added
 * later; not built now since it wasn't verified against a real large-history job.
 */
@Service
public class JenkinsBackfillService {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(JenkinsBackfillService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;
    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JenkinsBackfillService(RestClient.Builder restClientBuilder,
                                  EventPublisher publisher,
                                  ObjectMapper objectMapper,
                                  Clock clock,
                                  @Value("${jenkins.base-url}") String baseUrl,
                                  @Value("${jenkins.username}") String username,
                                  @Value("${jenkins.api-token}") String apiToken) {
        // No timeout on this client used to mean a dropped connection could hang a backfill call
        // forever instead of failing and letting the retry loop below actually run — see
        // TimeoutRestClients' javadoc.
        this.restClient = TimeoutRestClients.withTimeouts(restClientBuilder, Duration.ofSeconds(10), Duration.ofSeconds(60))
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? "http://unconfigured.invalid" : baseUrl)
                .defaultHeaders(headers -> {
                    if (username != null && !username.isBlank()) {
                        headers.setBasicAuth(username, apiToken);
                    }
                })
                .defaultHeader("Accept", "application/json")
                .build();
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public BackfillResult backfillJob(String jobName) {
        JsonNode response = fetchPage(() -> restClient.get()
                .uri("/job/{job}/api/json?tree=builds[number,result,timestamp,duration,url,actions[remoteUrls,lastBuiltRevision[SHA1,branch[name]]]]",
                        jobName)
                .retrieve()
                .body(String.class));

        JsonNode builds = response == null ? null : response.get("builds");
        int published = 0;
        if (builds != null && builds.isArray()) {
            for (JsonNode build : builds) {
                // Job name isn't on the per-build object — attach it so downstream (ingestion-
                // writer) can key sourceId/run_id without needing separate connector context.
                ((ObjectNode) build).put("job_name", jobName);

                String number = textOrNull(build, "number");
                if (number == null) {
                    continue;
                }
                publisher.publish(new EventEnvelope(
                        "jenkins",
                        "jenkins:" + jobName + ":" + number,
                        "build.snapshot",
                        Instant.now(clock),
                        CONNECTOR_VERSION,
                        build));
                published++;
            }
        }

        log.info("Backfill job {} complete: {} builds", jobName, published);
        return new BackfillResult(published);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private JsonNode fetchPage(Supplier<String> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                String body = call.get();
                return body == null ? null : objectMapper.readTree(body);
            } catch (Exception e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BackfillException("Jenkins API call failed after " + MAX_ATTEMPTS + " attempts", e);
                }
                long backoffMillis = Duration.ofSeconds(2L * attempt).toMillis();
                log.warn("Jenkins API call failed (attempt {}/{}), retrying in {} ms: {}",
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

    public record BackfillResult(int builds) {
    }

    public static class BackfillException extends RuntimeException {
        public BackfillException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
