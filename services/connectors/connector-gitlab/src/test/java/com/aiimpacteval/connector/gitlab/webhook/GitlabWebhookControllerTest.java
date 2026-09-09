package com.aiimpacteval.connector.gitlab.webhook;

import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.gitlab.events.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

@WebMvcTest(GitlabWebhookController.class)
@TestPropertySource(properties = "gitlab.webhook-secret=test-secret")
class GitlabWebhookControllerTest {

    private static final String SECRET = "test-secret";
    private static final String BODY = "{\"object_kind\":\"merge_request\",\"object_attributes\":{\"iid\":42}}";
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
    void publishesEnvelopeForValidToken() throws Exception {
        mockMvc.perform(post("/webhooks/gitlab")
                        .header("X-Gitlab-Token", SECRET)
                        .header("X-Gitlab-Event", "Merge Request Hook")
                        .content(BODY))
                .andExpect(status().isAccepted());

        assertEquals(1, publisher.published.size());
        EventEnvelope envelope = publisher.published.get(0);
        assertEquals("gitlab", envelope.source());
        assertEquals(sha256Hex(BODY), envelope.sourceId());
        assertEquals("merge_request", envelope.eventType());
        assertEquals(FIXED_NOW, envelope.receivedAt());
        assertEquals(42, envelope.payload().get("object_attributes").get("iid").asInt());
    }

    @Test
    void rejectsInvalidTokenWithoutPublishing() throws Exception {
        mockMvc.perform(post("/webhooks/gitlab")
                        .header("X-Gitlab-Token", "wrong-secret")
                        .header("X-Gitlab-Event", "Merge Request Hook")
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        assertTrue(publisher.published.isEmpty());
    }

    @Test
    void rejectsMissingTokenHeader() throws Exception {
        mockMvc.perform(post("/webhooks/gitlab")
                        .header("X-Gitlab-Event", "Push Hook")
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        assertTrue(publisher.published.isEmpty());
    }

    @Test
    void rejectsMissingObjectKind() throws Exception {
        mockMvc.perform(post("/webhooks/gitlab")
                        .header("X-Gitlab-Token", SECRET)
                        .header("X-Gitlab-Event", "System Hook")
                        .content("{\"event_name\":\"project_create\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(publisher.published.isEmpty());
    }

    private static String sha256Hex(String body) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    }
}
