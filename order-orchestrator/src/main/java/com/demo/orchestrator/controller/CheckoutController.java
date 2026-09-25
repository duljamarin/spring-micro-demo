package com.demo.orchestrator.controller;

import com.demo.orchestrator.client.OrderDto;
import com.demo.orchestrator.dto.CheckoutRequest;
import com.demo.orchestrator.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    /**
     * X-User-Id is trusted here because api-gateway already verified the JWT
     * and set it - this service is never reachable directly from outside the
     * cluster (no public route bypasses the gateway).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto checkout(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody CheckoutRequest request) {
        return checkoutService.checkout(userId, request);
    }
}
