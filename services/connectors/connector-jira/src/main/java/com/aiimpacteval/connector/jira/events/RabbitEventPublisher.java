package com.aiimpacteval.connector.jira.events;

import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.common.events.EventTopology;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(EventEnvelope envelope) {
        rabbitTemplate.convertAndSend(EventTopology.EVENTS_EXCHANGE, envelope.routingKey(), envelope);
    }
}
