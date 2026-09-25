package com.demo.cart.dto;

import com.demo.cart.model.Cart;

import java.util.List;

public record CartResponse(Long userId, List<CartItemResponse> items) {
    public static CartResponse from(Cart cart) {
        return new CartResponse(cart.getUserId(), cart.getItems().stream().map(CartItemResponse::from).toList());
    }
}
