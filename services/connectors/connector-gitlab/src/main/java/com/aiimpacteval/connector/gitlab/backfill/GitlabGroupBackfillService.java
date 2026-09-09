package com.aiimpacteval.connector.gitlab.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.gitlab.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;

/**
 * Backfills a GitLab group's team structure (PRD E2-S2, FR-1.5): the group's projects and its
 * members. GitLab has no first-class "team" object — a group (optionally with subgroups) is
 * the closest analogue to a GitHub org team, so one {@code group.snapshot} event is published
 * per group. Connectors publish raw data; multi-call assembly of a group's own sub-resources
 * is still data transfer, not metric logic (ADR-0002). The identity service (E2) owns turning
 * this into {@code core.team}/{@code core.team_repo}/{@code core.team_member}.
 *
 * <p>Like GitHub's Teams API, GitLab's group members/projects endpoints expose no single
 * "updated at" for the group as a whole, so the sourceId includes the current instant rather
 * than an entity timestamp — every run publishes a fresh snapshot, and the identity service's
 * own upsert logic (not staging's natural-key idempotency) keeps repeated imports safe.
 */
@Service
public class GitlabGroupBackfillService {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(GitlabGroupBackfillService.class);
    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final EventPublisher publisher;
    private final RetryingJsonFetcher fetcher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GitlabGroupBackfillService(RestClient.Builder restClientBuilder,
                                      EventPublisher publisher,
                                      ObjectMapper objectMapper,
                                      Clock clock,
                                      @Value("${gitlab.api-base-url}") String apiBaseUrl,
                                      @Value("${gitlab.token}") String token) {
        this.restClient = GitlabRestClients.build(restClientBuilder, apiBaseUrl, token);
        this.publisher = publisher;
        this.fetcher = new RetryingJsonFetcher(objectMapper, clock);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param group numeric group ID, or the URL-encodable group/subgroup path.
     */
    public GroupBackfillResult backfillGroup(String group) {
        JsonNode groupDetail = fetcher.fetch(() -> restClient.get()
                .uri("/groups/{group}", group)
                .retrieve()
                .body(String.class));
        if (groupDetail == null) {
            return new GroupBackfillResult(0);
        }
        publishGroupSnapshot(group, groupDetail);
        log.info("Group backfill for {} complete", group);
        return new GroupBackfillResult(1);
    }

    private void publishGroupSnapshot(String group, JsonNode groupDetail) {
        ArrayNode projects = fetchAllPages("/groups/{group}/projects?include_subgroups=true", group, "path_with_namespace");
        // members/all includes members inherited from ancestor groups, not just direct members —
        // matters for subgroup-scoped connects where most access comes from a parent group.
        ArrayNode members = fetchAllPages("/groups/{group}/members/all", group, "username");

        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("id", groupDetail.get("id").asLong());
        snapshot.put("name", groupDetail.get("name").asText());
        snapshot.put("path", groupDetail.get("full_path").asText());
        snapshot.set("projects", projects);
        snapshot.set("members", members);

        publisher.publish(new EventEnvelope(
                "gitlab",
                "group:" + groupDetail.get("id").asLong() + ":" + Instant.now(clock),
                "group.snapshot",
                Instant.now(clock),
                CONNECTOR_VERSION,
                snapshot));
    }

    private ArrayNode fetchAllPages(String pathTemplate, String group, String requiredField) {
        ArrayNode all = objectMapper.createArrayNode();
        for (int page = 1; ; page++) {
            final int currentPage = page;
            String separator = pathTemplate.contains("?") ? "&" : "?";
            JsonNode items = fetcher.fetch(() -> restClient.get()
                    .uri(pathTemplate + separator + "per_page={size}&page={page}", group, PAGE_SIZE, currentPage)
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

    public record GroupBackfillResult(int groups) {
    }
}
