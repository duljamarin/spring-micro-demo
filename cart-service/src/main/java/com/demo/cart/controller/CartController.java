package com.demo.cart.controller;

import com.demo.cart.dto.AddItemRequest;
import com.demo.cart.dto.CartResponse;
import com.demo.cart.dto.CheckoutItemResult;
import com.demo.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Every endpoint takes the caller's identity from X-User-Id, set by
 * api-gateway after verifying the JWT - there's no "whose cart" parameter
 * because it's never the client's to choose.
 */
@RestController
@RequestMapping("/api/carts/me")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(@RequestHeader("X-User-Id") Long userId) {
        return CartResponse.from(cartService.getOrCreateCart(userId));
    }

    @PostMapping("/items")
    public CartResponse addItem(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody AddItemRequest request) {
        return CartResponse.from(cartService.addItem(userId, request.productId(), request.quantity()));
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@RequestHeader("X-User-Id") Long userId, @PathVariable Long productId) {
        return CartResponse.from(cartService.removeItem(userId, productId));
    }

    @PostMapping("/checkout")
    public List<CheckoutItemResult> checkout(@RequestHeader("X-User-Id") Long userId) {
        return cartService.checkout(userId);
    }
}
