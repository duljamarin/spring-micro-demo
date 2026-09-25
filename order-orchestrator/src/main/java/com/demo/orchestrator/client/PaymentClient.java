package com.demo.orchestrator.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/api/payments/charge")
    PaymentDto charge(@RequestBody ChargeRequest request);

    @PostMapping("/api/payments/{id}/refund")
    PaymentDto refund(@PathVariable("id") Long id);
}
