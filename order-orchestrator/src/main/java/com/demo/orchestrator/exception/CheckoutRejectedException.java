package com.demo.orchestrator.exception;

/** The saga ran to completion but a business rule said no (no such product, insufficient stock, payment declined). */
public class CheckoutRejectedException extends RuntimeException {
    public CheckoutRejectedException(String message) {
        super(message);
    }
}
