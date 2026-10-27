package com.demo.review.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Own bean so the resilience4j aspects actually apply (a self-call inside one
 * bean bypasses the proxy - see ResilientProductClient in order-orchestrator
 * for the same note).
 *
 * Product names are cosmetic here: a score belongs to a productId and stays
 * correct whether or not the catalog answers. So both methods degrade to
 * "nothing known" rather than failing the request - a scoreboard showing
 * "Product #2" with real scores is far more useful than a 503.
 */
@Component
public class ProductCatalog {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalog.class);

    private final ProductClient productClient;

    public ProductCatalog(ProductClient productClient) {
        this.productClient = productClient;
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "noCatalog")
    public List<ProductDto> catalog() {
        return productClient.findAll();
    }

    @SuppressWarnings("unused") // resilience4j resolves fallbacks by name
    private List<ProductDto> noCatalog(Exception ex) {
        log.warn("product-service unavailable ({}); scoreboard falls back to placeholder names", ex.getMessage());
        return List.of();
    }

    /**
     * Used when scoring a single review, to give the model the product name
     * as context - "27-inch Monitor" is what tells it that "the panel" in the
     * review text means the product. Optional because this must never block
     * scoring.
     */
    @CircuitBreaker(name = "productService", fallbackMethod = "noName")
    public Optional<String> nameOf(Long productId) {
        return Optional.ofNullable(productClient.findById(productId)).map(ProductDto::name);
    }

    @SuppressWarnings("unused")
    private Optional<String> noName(Long productId, Exception ex) {
        log.warn("could not resolve name for product {} ({})", productId, ex.getMessage());
        return Optional.empty();
    }
}
