package com.demo.notification.event;

import java.math.BigDecimal;

/** notification-service's own copy of the event contract order-orchestrator publishes. */
public record OrderEvent(
        String eventType,
        Long orderId,
        Long userId,
        String productName,
        int quantity,
        BigDecimal totalPrice,
        String reason
) {
}
