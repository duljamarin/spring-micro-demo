package com.demo.orchestrator.client;

import java.math.BigDecimal;

public record CreateOrderRequest(Long userId, Long productId, String productName, int quantity, BigDecimal unitPrice) {
}
