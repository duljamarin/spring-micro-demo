package com.demo.orchestrator.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

@Component
public class ResilientPaymentClient {

    private final PaymentClient paymentClient;

    public ResilientPaymentClient(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @CircuitBreaker(name = "paymentService")
    @Retry(name = "paymentService")
    public PaymentDto charge(ChargeRequest request) {
        return paymentClient.charge(request);
    }

    /** Best-effort compensation, same reasoning as ResilientProductClient.releaseStock. */
    @Retry(name = "paymentService")
    public PaymentDto refund(Long paymentId) {
        return paymentClient.refund(paymentId);
    }
}
