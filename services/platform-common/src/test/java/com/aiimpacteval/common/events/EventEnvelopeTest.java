package com.aiimpacteval.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventEnvelopeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void routingKeyIsSourceDotEventType() {
        var envelope = new EventEnvelope(
                "github", "delivery-guid", "pull_request",
                Instant.parse("2026-07-04T12:00:00Z"), "0.1.0",
                MAPPER.createObjectNode());
        assertEquals("github.pull_request", envelope.routingKey());
    }

    @Test
    void rejectsMissingFields() {
        assertThrows(NullPointerException.class, () -> new EventEnvelope(
                "github", null, "push", Instant.now(), "0.1.0", MAPPER.createObjectNode()));
    }
}
