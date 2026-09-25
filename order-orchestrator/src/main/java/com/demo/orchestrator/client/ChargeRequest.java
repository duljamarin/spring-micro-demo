package com.demo.orchestrator.client;

import java.math.BigDecimal;

public record ChargeRequest(Long orderId, Long userId, BigDecimal amount) {
}
