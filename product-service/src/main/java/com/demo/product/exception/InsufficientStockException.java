package com.demo.product.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super("Insufficient stock for product %d: requested %d, available %d"
                .formatted(productId, requested, available));
    }
}
