package com.demo.order.model;

public enum OrderStatus {
    /** Created by the orchestrator; payment hasn't been confirmed yet. */
    PENDING,
    CONFIRMED,
    FAILED
}
