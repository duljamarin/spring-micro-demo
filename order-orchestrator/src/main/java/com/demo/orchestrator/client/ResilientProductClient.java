package com.demo.orchestrator.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

/**
 * Own bean (not methods on CheckoutService) so the @CircuitBreaker/@Retry
 * aspects actually apply - see the note on this exact pitfall in
 * order-service's git history/README: a call from one method to another on
 * the same bean never goes through the Spring AOP proxy, so annotations on
 * "sibling" methods would silently do nothing.
 */
@Component
public class ResilientProductClient {

    private final ProductClient productClient;

    public ResilientProductClient(ProductClient productClient) {
        this.productClient = productClient;
    }

    @CircuitBreaker(name = "productService")
    @Retry(name = "productService")
    public ProductDto reserveStock(Long productId, int quantity) {
        return productClient.reserveStock(productId, new StockQuantityRequest(quantity));
    }

    /**
     * No circuit breaker here on purpose: this runs as a compensating action
     * after a failure has already happened, so short-circuiting it would only
     * mean the compensation itself silently doesn't happen. Best-effort with
     * a retry is the right tradeoff - the caller still logs and surfaces the
     * failure if every attempt is exhausted.
     */
    @Retry(name = "productService")
    public ProductDto releaseStock(Long productId, int quantity) {
        return productClient.releaseStock(productId, new StockQuantityRequest(quantity));
    }
}
