package com.demo.orchestrator.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One product per checkout call, matching order-service's current single-item
 * Order model. cart-service (which does hold multiple line items) calls this
 * once per line item and aggregates the results - modeling a true multi-item
 * order would mean an OrderItem entity in order-service, which is a bigger
 * change than this demo's saga is trying to illustrate.
 */
public record CheckoutRequest(@NotNull Long productId, @Min(1) int quantity) {
}
