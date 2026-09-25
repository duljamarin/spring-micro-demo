package com.demo.order.dto;

import com.demo.order.model.Order;
import com.demo.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        Long userId,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        OrderStatus status,
        String failureReason,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getProductId(),
                order.getProductName(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalPrice(),
                order.getStatus(),
                order.getFailureReason(),
                order.getCreatedAt()
        );
    }
}
