package com.demo.cart.dto;

public record CheckoutItemResult(Long productId, boolean success, Long orderId, String message) {
    public static CheckoutItemResult success(Long productId, Long orderId) {
        return new CheckoutItemResult(productId, true, orderId, "Order placed");
    }

    public static CheckoutItemResult failure(Long productId, String message) {
        return new CheckoutItemResult(productId, false, null, message);
    }

    /**
     * Distinct from {@link #failure} on purpose: nothing was sent downstream
     * for this item, so there's no chance of an order having been placed for
     * it. Retrying it is always safe, which isn't something a caller can
     * assume about an item that actually failed.
     */
    public static CheckoutItemResult notAttempted(Long productId) {
        return new CheckoutItemResult(productId, false, null,
                "Not attempted - checkout time budget was spent; item left in cart, retry to continue");
    }
}
