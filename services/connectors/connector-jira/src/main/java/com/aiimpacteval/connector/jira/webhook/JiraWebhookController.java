package com.aiimpacteval.connector.jira.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.jira.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@RestController
public class JiraWebhookController {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(JiraWebhookController.class);

    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String webhookSecret;

    public JiraWebhookController(EventPublisher publisher,
                                 ObjectMapper objectMapper,
                                 Clock clock,
                                 @Value("${jira.webhook-secret}") String webhookSecret) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhooks/jira")
    public ResponseEntity<Void> receive(@RequestParam(value = "token", required = false) String token,
                                        @RequestBody byte[] rawBody) {
        if (!WebhookTokenVerifier.isValid(webhookSecret, token)) {
            log.warn("Rejected Jira webhook: token verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }

        JsonNode eventField = payload.get("webhookEvent");
        if (eventField == null || eventField.asText().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Jira sends no delivery GUID — the body hash is the idempotency key, so identical
        // redeliveries dedupe in staging (ADR-0003).
        publisher.publish(new EventEnvelope(
                "jira", sha256Hex(rawBody), eventField.asText(),
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
