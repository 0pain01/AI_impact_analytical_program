package com.aiimpacteval.common.events;

/** RabbitMQ topology names shared by publishers and consumers (ADR-0003). */
public final class EventTopology {

    public static final String EVENTS_EXCHANGE = "aiimpacteval.events";
    public static final String DEAD_LETTER_EXCHANGE = "aiimpacteval.events.dlx";

    public static final String STAGING_QUEUE = "staging.events";
    public static final String STAGING_DLQ = "staging.events.dlq";
    /** The staging writer persists every event, whatever the source. */
    public static final String STAGING_BINDING = "#";

    private EventTopology() {
    }
}
