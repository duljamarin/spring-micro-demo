package com.demo.orchestrator.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(OrderEvent event) {
        String routingKey = event.eventType().equals(OrderEvent.ORDER_CONFIRMED) ? "order.confirmed" : "order.failed";
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EVENTS_EXCHANGE, routingKey, event);
        } catch (Exception ex) {
            // The saga's outcome is already durably recorded in order-service;
            // losing this event only means a notification doesn't go out, so
            // it's logged rather than allowed to fail the checkout response.
            log.warn("Failed to publish {} for order {}: {}", event.eventType(), event.orderId(), ex.getMessage());
        }
    }
}
