package com.aiimpacteval.connector.jenkins.events;

import com.aiimpacteval.common.events.EventEnvelope;

/** Port for publishing connector events; RabbitMQ in production, recording stub in tests. */
public interface EventPublisher {

    void publish(EventEnvelope envelope);
}
