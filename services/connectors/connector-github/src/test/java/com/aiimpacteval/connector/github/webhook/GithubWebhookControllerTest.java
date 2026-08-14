package com.aiimpacteval.connector.github.webhook;

import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.github.events.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GithubWebhookController.class)
@TestPropertySource(properties = "github.webhook-secret=test-secret")
class GithubWebhookControllerTest {

    private static final String SECRET = "test-secret";
    private static final String BODY = "{\"action\":\"opened\",\"number\":42}";
    private static final Instant FIXED_NOW = Instant.parse("2026-07-04T12:00:00Z");

    /** Recording stub instead of a mocking framework (Byte Buddy cannot instrument on newer JDKs). */
    static class RecordingEventPublisher implements EventPublisher {
        final List<EventEnvelope> published = new ArrayList<>();

        @Override
        public void publish(EventEnvelope envelope) {
            published.add(envelope);
        }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }

        @Bean
        RecordingEventPublisher eventPublisher() {
            return new RecordingEventPublisher();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEventPublisher publisher;

    @BeforeEach
    void resetPublisher() {
        publisher.published.clear();
    }

    @Test
    void publishesEnvelopeForValidSignature() throws Exception {
        mockMvc.perform(post("/webhooks/github")
                        .header("X-Hub-Signature-256", sign(SECRET, BODY))
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-guid-1")
                        .content(BODY))
                .andExpect(status().isAccepted());

        assertEquals(1, publisher.published.size());
        EventEnvelope envelope = publisher.published.get(0);
        assertEquals("github", envelope.source());
        assertEquals("delivery-guid-1", envelope.sourceId());
        assertEquals("pull_request", envelope.eventType());
        assertEquals(FIXED_NOW, envelope.receivedAt());
        assertEquals(42, envelope.payload().get("number").asInt());
    }

    @Test
    void rejectsInvalidSignatureWithoutPublishing() throws Exception {
        mockMvc.perform(post("/webhooks/github")
                        .header("X-Hub-Signature-256", sign("wrong-secret", BODY))
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-guid-2")
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        assertTrue(publisher.published.isEmpty());
    }

    @Test
    void rejectsMissingSignatureHeader() throws Exception {
        mockMvc.perform(post("/webhooks/github")
                        .header("X-GitHub-Event", "push")
                        .header("X-GitHub-Delivery", "delivery-guid-3")
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        assertTrue(publisher.published.isEmpty());
    }

    private static String sign(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
