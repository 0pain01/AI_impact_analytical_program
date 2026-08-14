package com.aiimpacteval.connector.jira.webhook;

import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.jira.events.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JiraWebhookController.class)
@TestPropertySource(properties = "jira.webhook-secret=test-token")
class JiraWebhookControllerTest {

    private static final String BODY =
            "{\"timestamp\":1751630400000,\"webhookEvent\":\"jira:issue_updated\","
                    + "\"issue\":{\"id\":\"10042\",\"fields\":{\"summary\":\"Fix login bug\"}}}";
    private static final Instant FIXED_NOW = Instant.parse("2026-07-04T12:00:00Z");

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
        mockMvc.perform(post("/webhooks/jira").param("token", "test-token").content(BODY))
                .andExpect(status().isAccepted());

        assertEquals(1, publisher.published.size());
        EventEnvelope envelope = publisher.published.get(0);
        assertEquals("jira", envelope.source());
        assertEquals("jira:issue_updated", envelope.eventType());
        assertEquals(FIXED_NOW, envelope.receivedAt());
        assertEquals(64, envelope.sourceId().length(), "sourceId is the sha-256 body hash");
        assertEquals("10042", envelope.payload().get("issue").get("id").asText());
    }

    @Test
    void identicalRedeliveryProducesSameSourceId() throws Exception {
        mockMvc.perform(post("/webhooks/jira").param("token", "test-token").content(BODY))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/webhooks/jira").param("token", "test-token").content(BODY))
                .andExpect(status().isAccepted());

        assertEquals(2, publisher.published.size());
        assertEquals(publisher.published.get(0).sourceId(), publisher.published.get(1).sourceId(),
                "same body must map to the same idempotency key for staging dedup");
    }

    @Test
    void rejectsWrongOrMissingToken() throws Exception {
        mockMvc.perform(post("/webhooks/jira").param("token", "wrong").content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/webhooks/jira").content(BODY))
                .andExpect(status().isUnauthorized());

        assertTrue(publisher.published.isEmpty());
    }

    @Test
    void rejectsPayloadWithoutWebhookEvent() throws Exception {
        mockMvc.perform(post("/webhooks/jira").param("token", "test-token").content("{\"foo\":1}"))
                .andExpect(status().isBadRequest());

        assertTrue(publisher.published.isEmpty());
    }
}
