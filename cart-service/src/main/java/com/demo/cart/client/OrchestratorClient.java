package com.demo.cart.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Called directly service-to-service via Eureka, not through api-gateway -
 * the X-User-Id header is forwarded explicitly because that trust boundary
 * (gateway verified the JWT) was already crossed once when the client's
 * original request reached *this* service; order-orchestrator doesn't
 * re-verify it, matching the "trusted internal network" model this system
 * uses everywhere behind the gateway.
 */
@FeignClient(name = "order-orchestrator")
public interface OrchestratorClient {

    @PostMapping("/api/checkout")
    OrderDto checkout(@RequestHeader("X-User-Id") Long userId, @RequestBody CheckoutRequest request);
}
