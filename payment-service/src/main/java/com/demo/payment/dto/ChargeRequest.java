package com.demo.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ChargeRequest(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount
) {
}
