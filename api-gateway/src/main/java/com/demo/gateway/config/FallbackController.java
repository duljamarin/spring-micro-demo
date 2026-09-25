package com.demo.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Served when a downstream service's circuit breaker is open. Keeps the
 * gateway responsive with a clear, structured error instead of hanging or
 * propagating a raw connection failure to the client.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/products")
    public ResponseEntity<Map<String, Object>> productsFallback() {
        return fallback("product-service");
    }

    @RequestMapping("/fallback/orders")
    public ResponseEntity<Map<String, Object>> ordersFallback() {
        return fallback("order-service");
    }

    @RequestMapping("/fallback/users")
    public ResponseEntity<Map<String, Object>> usersFallback() {
        return fallback("user-service");
    }

    @RequestMapping("/fallback/carts")
    public ResponseEntity<Map<String, Object>> cartsFallback() {
        return fallback("cart-service");
    }

    @RequestMapping("/fallback/checkout")
    public ResponseEntity<Map<String, Object>> checkoutFallback() {
        return fallback("order-orchestrator");
    }

    private ResponseEntity<Map<String, Object>> fallback(String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "timestamp", Instant.now().toString(),
                "service", service,
                "message", service + " is currently unavailable. Please try again shortly.",
                "status", HttpStatus.SERVICE_UNAVAILABLE.value()
        ));
    }
}
