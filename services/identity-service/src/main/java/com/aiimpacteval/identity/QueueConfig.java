package com.aiimpacteval.identity;

import com.aiimpacteval.common.events.EventTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Own queue with a selective view of the event stream (ADR-0003: consumers add queues without
 * touching connectors). Binds `#` and filters in code — identity-relevant payload shapes evolve
 * faster than bindings should.
 */
@Configuration
public class QueueConfig {

    static final String IDENTITY_QUEUE = "identity.events";
    static final String IDENTITY_DLQ = "identity.events.dlq";

    @Bean
    TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EventTopology.EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange(EventTopology.DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue identityQueue() {
        return QueueBuilder.durable(IDENTITY_QUEUE)
                .deadLetterExchange(EventTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(IDENTITY_DLQ)
                .build();
    }

    @Bean
    Queue identityDeadLetterQueue() {
        return QueueBuilder.durable(IDENTITY_DLQ).build();
    }

    @Bean
    Binding identityBinding(Queue identityQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(identityQueue).to(eventsExchange).with("#");
    }

    @Bean
    Binding identityDlqBinding(Queue identityDeadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(identityDeadLetterQueue).to(deadLetterExchange).with(IDENTITY_DLQ);
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
