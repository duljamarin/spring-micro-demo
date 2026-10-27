package com.demo.review.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Only the catalog fields the scoreboard needs. {@code @JsonIgnoreProperties}
 * keeps this from breaking when product-service adds a field - a consumer
 * that deserializes strictly turns every producer-side addition into a
 * breaking change.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(
        Long id,
        String name,
        BigDecimal price
) {
}
