package com.demo.cart.dto;

public record CheckoutItemResult(Long productId, boolean success, Long orderId, String message) {
    public static CheckoutItemResult success(Long productId, Long orderId) {
        return new CheckoutItemResult(productId, true, orderId, "Order placed");
    }

    public static CheckoutItemResult failure(Long productId, String message) {
        return new CheckoutItemResult(productId, false, null, message);
    }
}
