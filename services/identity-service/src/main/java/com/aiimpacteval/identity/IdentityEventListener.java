package com.aiimpacteval.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.identity.resolve.IdentityResolver;
import com.aiimpacteval.identity.resolve.ObservedIdentity;
import com.aiimpacteval.identity.team.TeamImportService;
import com.aiimpacteval.identity.team.TeamSnapshotParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts identity observations from raw events and feeds the resolver (E2-S1), and routes
 * team-structure snapshots to the team importer (E2-S2). Payload shapes: webhook events nest
 * the entity; backfill snapshots are the entity.
 */
@Component
public class IdentityEventListener {

    private static final String TEAM_SNAPSHOT_EVENT_TYPE = "team.snapshot";

    private static final Logger log = LoggerFactory.getLogger(IdentityEventListener.class);

    private final IdentityResolver resolver;
    private final TeamImportService teamImportService;

    public IdentityEventListener(IdentityResolver resolver, TeamImportService teamImportService) {
        this.resolver = resolver;
        this.teamImportService = teamImportService;
    }

    @RabbitListener(queues = QueueConfig.IDENTITY_QUEUE)
    public void onEvent(EventEnvelope envelope) {
        if ("github".equals(envelope.source()) && TEAM_SNAPSHOT_EVENT_TYPE.equals(envelope.eventType())) {
            teamImportService.importSnapshot(TeamSnapshotParser.parse(envelope.payload()));
            return;
        }
        for (ObservedIdentity observed : extract(envelope)) {
            resolver.resolve(observed);
        }
    }

    List<ObservedIdentity> extract(EventEnvelope envelope) {
        List<ObservedIdentity> found = new ArrayList<>();
        JsonNode payload = envelope.payload();
        try {
            switch (envelope.source()) {
                case "github" -> extractGithub(envelope.eventType(), payload, found);
                case "jira" -> extractJira(payload, found);
                default -> { /* unknown source: nothing identity-relevant */ }
            }
        } catch (RuntimeException e) {
            // Extraction must never poison the queue over one odd payload; unresolved
            // identities are a data-quality signal, not a crash.
            log.warn("Identity extraction failed for {}/{}: {}",
                    envelope.source(), envelope.sourceId(), e.getMessage());
        }
        return found;
    }

    private void extractGithub(String eventType, JsonNode payload, List<ObservedIdentity> found) {
        if (eventType.startsWith("pull_request")) {
            JsonNode pr = payload.has("pull_request") ? payload.get("pull_request") : payload;
            addGithubUser(pr.path("user"), found);
        } else if (eventType.startsWith("commit")) {
            addCommit(payload, found);
        } else if (eventType.equals("push")) {
            payload.path("commits").forEach(c -> addGitSignature(c.path("author"), found));
        }
    }

    private void addCommit(JsonNode commit, List<ObservedIdentity> found) {
        JsonNode ghUser = commit.path("author");
        JsonNode gitAuthor = commit.path("commit").path("author");
        if (ghUser.hasNonNull("id")) {
            found.add(new ObservedIdentity("github", ghUser.path("id").asText(),
                    textOrNull(gitAuthor, "name") != null ? gitAuthor.get("name").asText() : textOrNull(ghUser, "login"),
                    textOrNull(gitAuthor, "email")));
        } else {
            addGitSignature(gitAuthor, found);
        }
    }

    private void addGithubUser(JsonNode user, List<ObservedIdentity> found) {
        if (user.hasNonNull("id")) {
            found.add(new ObservedIdentity("github", user.get("id").asText(),
                    textOrNull(user, "login"), textOrNull(user, "email")));
        }
    }

    /** Git signature without a platform account id — email is the only stable handle. */
    private void addGitSignature(JsonNode author, List<ObservedIdentity> found) {
        String email = textOrNull(author, "email");
        if (email != null) {
            found.add(new ObservedIdentity("github", "email:" + email.toLowerCase(),
                    textOrNull(author, "name"), email));
        }
    }

    private void extractJira(JsonNode payload, List<ObservedIdentity> found) {
        JsonNode issue = payload.has("issue") ? payload.get("issue") : payload;
        JsonNode fields = issue.path("fields");
        addJiraUser(fields.path("assignee"), found);
        addJiraUser(fields.path("reporter"), found);
        addJiraUser(payload.path("user"), found); // webhook actor
    }

    private void addJiraUser(JsonNode user, List<ObservedIdentity> found) {
        if (user.hasNonNull("accountId")) {
            found.add(new ObservedIdentity("jira", user.get("accountId").asText(),
                    textOrNull(user, "displayName"), textOrNull(user, "emailAddress")));
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
