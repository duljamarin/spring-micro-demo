package com.demo.orchestrator.client;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderDto(
        Long id,
        Long userId,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String status,
        String failureReason,
        Instant createdAt
) {
}
