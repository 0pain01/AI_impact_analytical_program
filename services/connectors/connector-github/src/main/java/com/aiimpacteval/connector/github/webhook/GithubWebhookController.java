package com.aiimpacteval.connector.github.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.github.events.EventPublisher;
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
import java.time.Clock;
import java.time.Instant;

@RestController
public class GithubWebhookController {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(GithubWebhookController.class);

    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String webhookSecret;

    public GithubWebhookController(EventPublisher publisher,
                                   ObjectMapper objectMapper,
                                   Clock clock,
                                   @Value("${github.webhook-secret}") String webhookSecret) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhooks/github")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestBody byte[] rawBody) {

        if (!WebhookSignatureVerifier.isValid(webhookSecret, rawBody, signature)) {
            log.warn("Rejected webhook delivery {} ({}): signature verification failed", deliveryId, eventType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }

        publisher.publish(new EventEnvelope(
                "github", deliveryId, eventType, Instant.now(clock), CONNECTOR_VERSION, payload));
        return ResponseEntity.accepted().build();
    }
}
