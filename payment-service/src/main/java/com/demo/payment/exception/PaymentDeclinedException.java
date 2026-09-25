package com.demo.payment.exception;

import java.math.BigDecimal;

/** A business outcome (insufficient funds, card declined, ...), not an infrastructure failure. */
public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(BigDecimal amount) {
        super("Payment declined for amount: " + amount);
    }
}
