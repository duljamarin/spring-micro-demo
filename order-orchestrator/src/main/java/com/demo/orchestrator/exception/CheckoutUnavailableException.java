package com.demo.orchestrator.exception;

/** A downstream participant in the saga is down/unreachable/timed out - an infrastructure failure, not a business one. */
public class CheckoutUnavailableException extends RuntimeException {
    public CheckoutUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
