package com.demo.notification.listener;

import com.demo.notification.event.OrderEvent;
import com.demo.notification.event.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * No real email/SMS provider is wired up - this simulates one by logging
 * what would have been sent. The point being demonstrated is the decoupling:
 * this service can be down, slow, or being redeployed and none of that
 * affects whether a checkout succeeds, because order-orchestrator never
 * waits on it.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void onOrderEvent(OrderEvent event) {
        switch (event.eventType()) {
            case "ORDER_CONFIRMED" -> log.info(
                    "[EMAIL SIMULATED] To user {}: Your order #{} for {}x {} (total {}) is confirmed!",
                    event.userId(), event.orderId(), event.quantity(), event.productName(), event.totalPrice());
            case "ORDER_FAILED" -> log.info(
                    "[EMAIL SIMULATED] To user {}: Your order #{} for {}x {} could not be placed: {}",
                    event.userId(), event.orderId(), event.quantity(), event.productName(), event.reason());
            default -> log.warn("Unrecognized order event type: {}", event.eventType());
        }
    }
}
