package com.demo.cart.client;

public record CheckoutRequest(Long productId, int quantity) {
}
