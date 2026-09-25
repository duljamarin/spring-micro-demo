package com.demo.payment.exception;

/**
 * The order already has a payment that this charge can't be treated as a
 * replay of - either it was refunded, or it's for a different amount. Not a
 * decline (the card was never asked) and not an outage, so it gets its own
 * status rather than being folded into either.
 */
public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException(String message) {
        super(message);
    }
}
