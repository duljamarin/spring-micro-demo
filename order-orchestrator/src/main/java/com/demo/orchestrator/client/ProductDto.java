package com.demo.orchestrator.client;

import java.math.BigDecimal;

public record ProductDto(Long id, String name, String description, BigDecimal price, int quantity) {
}
