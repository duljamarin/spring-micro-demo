package com.demo.orchestrator.client;

import java.math.BigDecimal;

public record PaymentDto(Long id, Long orderId, Long userId, BigDecimal amount, String status) {
}
