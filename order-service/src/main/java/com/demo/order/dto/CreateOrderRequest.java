package com.demo.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Issued only by order-orchestrator, which has already reserved the stock and knows the product's price. */
public record CreateOrderRequest(
        @NotNull Long userId,
        @NotNull Long productId,
        @NotBlank String productName,
        @Min(1) int quantity,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal unitPrice
) {
}
