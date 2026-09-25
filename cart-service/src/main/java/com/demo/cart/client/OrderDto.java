package com.demo.cart.client;

import java.math.BigDecimal;

public record OrderDto(Long id, Long productId, String productName, int quantity, BigDecimal totalPrice, String status) {
}
