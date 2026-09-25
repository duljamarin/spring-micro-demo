package com.demo.cart.service;

import com.demo.cart.CartProperties;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;
    private final OrchestratorClient orchestratorClient;
    private final CartProperties properties;

    public CartService(CartRepository cartRepository, OrchestratorClient orchestratorClient,
                       CartProperties properties) {
        this.cartRepository = cartRepository;
        this.orchestratorClient = orchestratorClient;
        this.properties = properties;
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
     *
     * <p>Because the lines run sequentially, total time grows with cart size,
     * and the gateway's TimeLimiter would eventually fire on a big cart -
     * handing the user a timeout while this loop kept placing orders behind
     * it, with no response left to tell them which ones succeeded. So the
     * loop stops starting new items once the budget is spent and returns what
     * it has. Stopping *before* a call rather than abandoning one in flight
     * is what makes the leftover items safe to retry.
     */
    public List<CheckoutItemResult> checkout(Long userId) {
        Cart cart = getOrCreateCart(userId);
        List<CheckoutItemResult> results = new ArrayList<>();
        List<CartItem> remaining = new ArrayList<>();

        long startNanos = System.nanoTime();
        Duration budget = properties.getCheckoutBudget();
        boolean budgetSpent = false;
        int notAttempted = 0;

        for (CartItem item : List.copyOf(cart.getItems())) {
            if (budgetSpent || (budgetSpent = elapsedSince(startNanos).compareTo(budget) >= 0)) {
                results.add(CheckoutItemResult.notAttempted(item.getProductId()));
                remaining.add(item);
                notAttempted++;
                continue;
            }
            try {
                OrderDto order = orchestratorClient.checkout(userId, new CheckoutRequest(item.getProductId(), item.getQuantity()));
                results.add(CheckoutItemResult.success(item.getProductId(), order.id()));
            } catch (Exception ex) {
                log.warn("Checkout failed for user {} product {}: {}", userId, item.getProductId(), ex.getMessage());
                results.add(CheckoutItemResult.failure(item.getProductId(), ex.getMessage()));
                remaining.add(item);
            }
        }

        if (budgetSpent) {
            log.warn("Checkout for user {} spent its {} budget after {} of {} items; {} left unattempted",
                    userId, budget, results.size() - notAttempted, results.size(), notAttempted);
        }

        cart.getItems().clear();
        remaining.forEach(item -> cart.addItem(item.getProductId(), item.getQuantity()));
        cartRepository.save(cart);
        return results;
    }

    private static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
