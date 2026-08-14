package com.aiimpacteval.common.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;

/**
 * Envelope for every event published by a connector (ADR-0003).
 *
 * <p>{@code (source, sourceId, eventType)} is the idempotency key matching the
 * {@code staging.raw_event} unique constraint — redeliveries and replays are no-ops.
 * {@code payload} carries the raw vendor body untouched; connectors own no business logic.
 */
public record EventEnvelope(
        String source,
        String sourceId,
        String eventType,
        Instant receivedAt,
        String connectorVersion,
        JsonNode payload) {

    public EventEnvelope {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(connectorVersion, "connectorVersion");
        Objects.requireNonNull(payload, "payload");
    }

    /** Routing key on the {@code aiimpacteval.events} exchange: {@code {source}.{eventType}}. */
    public String routingKey() {
        return source + "." + eventType;
    }
}
