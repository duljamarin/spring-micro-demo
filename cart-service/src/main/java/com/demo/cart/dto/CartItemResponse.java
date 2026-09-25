package com.demo.cart.dto;

import com.demo.cart.model.CartItem;

public record CartItemResponse(Long productId, int quantity) {
    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(item.getProductId(), item.getQuantity());
    }
}
