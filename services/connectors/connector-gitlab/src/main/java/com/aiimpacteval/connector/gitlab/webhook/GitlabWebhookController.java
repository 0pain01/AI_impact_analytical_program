package com.aiimpacteval.connector.gitlab.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.gitlab.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@RestController
public class GitlabWebhookController {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(GitlabWebhookController.class);

    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String webhookSecret;

    public GitlabWebhookController(EventPublisher publisher,
                                   ObjectMapper objectMapper,
                                   Clock clock,
                                   @Value("${gitlab.webhook-secret}") String webhookSecret) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhooks/gitlab")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
            @RequestBody byte[] rawBody) {

        if (!WebhookTokenVerifier.isValid(webhookSecret, token)) {
            log.warn("Rejected GitLab webhook: token verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }

        // object_kind (merge_request, push, tag_push, pipeline, job, note, ...) is GitLab's
        // own event-type field on every project webhook payload — same role as Jira's
        // webhookEvent field, and more reliable across GitLab versions than trying to derive
        // an event type from the human-readable X-Gitlab-Event header ("Merge Request Hook").
        JsonNode eventField = payload.get("object_kind");
        if (eventField == null || eventField.asText().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // GitLab's X-Gitlab-Event-UUID delivery header isn't guaranteed on older self-managed
        // instances, so — same as the Jira connector — the body hash is the idempotency key;
        // identical redeliveries dedupe in staging on (source, sourceId, eventType) (ADR-0003).
        publisher.publish(new EventEnvelope(
                "gitlab", sha256Hex(rawBody), eventField.asText(),
                Instant.now(clock), CONNECTOR_VERSION, payload));
        return ResponseEntity.accepted().build();
    }

    private static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
