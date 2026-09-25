package com.demo.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final String ORDER_EVENTS_EXCHANGE = "order-events";
    public static final String QUEUE_NAME = "notification-service.order-events";

    /**
     * A consumer declares (and binds) its own queue against the producer's
     * exchange by name - it doesn't need the producer's config class, and the
     * producer doesn't need to know this consumer exists. Any other service
     * that wants order events later just adds its own queue + binding here,
     * with zero changes to order-orchestrator.
     */
    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(orderEventsExchange).with("order.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        // order-orchestrator's OrderEvent and this service's OrderEvent are
        // deliberately separate classes in separate packages (each service
        // owns its own copy of the contract). The message carries a
        // __TypeId__ header naming the *producer's* class, which doesn't
        // exist on this classpath - INFERRED tells the converter to target
        // whatever type the @RabbitListener method actually declares instead
        // of trying to load the producer's class name.
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
