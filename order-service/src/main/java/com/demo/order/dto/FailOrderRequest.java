package com.demo.order.dto;

import jakarta.validation.constraints.NotBlank;

public record FailOrderRequest(@NotBlank String reason) {
}
