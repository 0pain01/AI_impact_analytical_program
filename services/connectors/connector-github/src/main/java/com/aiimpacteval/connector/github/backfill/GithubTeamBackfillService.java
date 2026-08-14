package com.aiimpacteval.connector.github.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.github.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;

/**
 * Backfills GitHub org team structure (PRD E2-S2, FR-1.5): teams, their repositories, and
 * their members. Assembles one {@code team.snapshot} event per team — connectors publish raw
 * data, but multi-call assembly of a team's own sub-resources is still data transfer, not
 * metric logic (ADR-0002). The identity service (E2) owns turning this into
 * {@code core.team}/{@code core.team_repo}/{@code core.team_member}.
 *
 * <p>GitHub's Teams API exposes no reliable "updated at" for a team, so unlike PR/commit/
 * workflow-run backfill, the sourceId includes the current instant rather than an entity
 * timestamp — every run publishes a fresh snapshot, and the identity service's own upsert
 * logic (not staging's natural-key idempotency) keeps repeated imports safe.
 */
@Service
public class GithubTeamBackfillService {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(GithubTeamBackfillService.class);
    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final EventPublisher publisher;
    private final RetryingJsonFetcher fetcher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GithubTeamBackfillService(RestClient.Builder restClientBuilder,
                                     EventPublisher publisher,
                                     ObjectMapper objectMapper,
                                     Clock clock,
                                     @Value("${github.api-base-url}") String apiBaseUrl,
                                     @Value("${github.token}") String token) {
        this.restClient = GithubRestClients.build(restClientBuilder, apiBaseUrl, token);
        this.publisher = publisher;
        this.fetcher = new RetryingJsonFetcher(objectMapper);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public TeamBackfillResult backfillOrgTeams(String org) {
        int teamCount = 0;
        for (int page = 1; ; page++) {
            final int currentPage = page;
            JsonNode teams = fetcher.fetch(() -> restClient.get()
                    .uri("/orgs/{org}/teams?per_page={size}&page={page}", org, PAGE_SIZE, currentPage)
                    .retrieve()
                    .body(String.class));
            if (teams == null || teams.isEmpty()) {
                break;
            }
            for (JsonNode team : teams) {
                publishTeamSnapshot(org, team);
                teamCount++;
            }
            if (teams.size() < PAGE_SIZE) {
                break;
            }
        }
        log.info("Team backfill for org {} complete: {} teams", org, teamCount);
        return new TeamBackfillResult(teamCount);
    }

    private void publishTeamSnapshot(String org, JsonNode team) {
        String slug = team.get("slug").asText();
        ArrayNode repositories = fetchAllPages("/orgs/{org}/teams/{slug}/repos", org, slug, "full_name");
        ArrayNode members = fetchAllPages("/orgs/{org}/teams/{slug}/members", org, slug, "login");

        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("id", team.get("id").asLong());
        snapshot.put("name", team.get("name").asText());
        snapshot.put("slug", slug);
        snapshot.set("repositories", repositories);
        snapshot.set("members", members);

        publisher.publish(new EventEnvelope(
                "github",
                "team:" + team.get("id").asLong() + ":" + Instant.now(clock),
                "team.snapshot",
                Instant.now(clock),
                CONNECTOR_VERSION,
                snapshot));
    }

    private ArrayNode fetchAllPages(String pathTemplate, String org, String slug, String requiredField) {
        ArrayNode all = objectMapper.createArrayNode();
        for (int page = 1; ; page++) {
            final int currentPage = page;
            JsonNode items = fetcher.fetch(() -> restClient.get()
                    .uri(pathTemplate + "?per_page={size}&page={page}", org, slug, PAGE_SIZE, currentPage)
                    .retrieve()
                    .body(String.class));
            if (items == null || items.isEmpty()) {
                return all;
            }
            for (JsonNode item : items) {
                if (item.hasNonNull(requiredField)) {
                    all.add(item);
                }
            }
            if (items.size() < PAGE_SIZE) {
                return all;
            }
        }
    }

    public record TeamBackfillResult(int teams) {
    }
}
