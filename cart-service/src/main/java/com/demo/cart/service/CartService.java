package com.demo.cart.service;

import com.demo.cart.client.CheckoutRequest;
import com.demo.cart.client.OrchestratorClient;
import com.demo.cart.client.OrderDto;
import com.demo.cart.dto.CheckoutItemResult;
import com.demo.cart.model.Cart;
import com.demo.cart.model.CartItem;
import com.demo.cart.repository.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;
    private final OrchestratorClient orchestratorClient;

    public CartService(CartRepository cartRepository, OrchestratorClient orchestratorClient) {
        this.cartRepository = cartRepository;
        this.orchestratorClient = orchestratorClient;
    }

    public Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> cartRepository.save(new Cart(userId)));
    }

    public Cart addItem(Long userId, Long productId, int quantity) {
        Cart cart = getOrCreateCart(userId);
        cart.addItem(productId, quantity);
        return cartRepository.save(cart);
    }

    public Cart removeItem(Long userId, Long productId) {
        Cart cart = getOrCreateCart(userId);
        cart.removeItem(productId);
        return cartRepository.save(cart);
    }

    /**
     * Runs each cart line through order-orchestrator's saga one at a time.
     * A failed line stays in the cart (so the user can retry or drop it
     * themselves); a successful line is removed. One cart can therefore end
     * a checkout partially fulfilled - that's surfaced explicitly in the
     * per-item results rather than hidden behind a single success/failure flag.
     */
    public List<CheckoutItemResult> checkout(Long userId) {
        Cart cart = getOrCreateCart(userId);
        List<CheckoutItemResult> results = new ArrayList<>();
        List<CartItem> remaining = new ArrayList<>();

        for (CartItem item : List.copyOf(cart.getItems())) {
            try {
                OrderDto order = orchestratorClient.checkout(userId, new CheckoutRequest(item.getProductId(), item.getQuantity()));
                results.add(CheckoutItemResult.success(item.getProductId(), order.id()));
            } catch (Exception ex) {
                log.warn("Checkout failed for user {} product {}: {}", userId, item.getProductId(), ex.getMessage());
                results.add(CheckoutItemResult.failure(item.getProductId(), ex.getMessage()));
                remaining.add(item);
            }
        }

        cart.getItems().clear();
        remaining.forEach(item -> cart.addItem(item.getProductId(), item.getQuantity()));
        cartRepository.save(cart);
        return results;
    }
}
