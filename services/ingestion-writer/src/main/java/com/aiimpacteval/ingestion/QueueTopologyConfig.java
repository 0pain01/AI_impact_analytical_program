package com.aiimpacteval.ingestion;

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
 * Declares the staging queue with dead-lettering per ADR-0003: poison messages are
 * quarantined in the DLQ, never dropped (engineering standards §7).
 */
@Configuration
public class QueueTopologyConfig {

    @Bean
    TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EventTopology.EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange(EventTopology.DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue stagingQueue() {
        return QueueBuilder.durable(EventTopology.STAGING_QUEUE)
                .deadLetterExchange(EventTopology.DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    Queue stagingDeadLetterQueue() {
        return QueueBuilder.durable(EventTopology.STAGING_DLQ).build();
    }

    @Bean
    Binding stagingBinding(Queue stagingQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(stagingQueue).to(eventsExchange).with(EventTopology.STAGING_BINDING);
    }

    @Bean
    Binding deadLetterBinding(Queue stagingDeadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(stagingDeadLetterQueue).to(deadLetterExchange).with("#");
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
