package com.demo.orchestrator.event;

import java.math.BigDecimal;

/**
 * Published to the "order-events" topic exchange after the saga finishes,
 * win or lose. Notification-service (and anything else that cares later -
 * analytics, loyalty points, ...) subscribes independently; the orchestrator
 * doesn't know or care who's listening, and doesn't block order placement on
 * them being up.
 */
public record OrderEvent(
        String eventType,
        Long orderId,
        Long userId,
        String productName,
        int quantity,
        BigDecimal totalPrice,
        String reason
) {
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_FAILED = "ORDER_FAILED";

    public static OrderEvent confirmed(Long orderId, Long userId, String productName, int quantity, BigDecimal totalPrice) {
        return new OrderEvent(ORDER_CONFIRMED, orderId, userId, productName, quantity, totalPrice, null);
    }

    public static OrderEvent failed(Long orderId, Long userId, String productName, int quantity, String reason) {
        return new OrderEvent(ORDER_FAILED, orderId, userId, productName, quantity, null, reason);
    }
}
