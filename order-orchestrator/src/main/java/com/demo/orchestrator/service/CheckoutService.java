package com.demo.orchestrator.service;

import com.demo.orchestrator.client.*;
import com.demo.orchestrator.dto.CheckoutRequest;
import com.demo.orchestrator.event.OrderEvent;
import com.demo.orchestrator.event.OrderEventPublisher;
import com.demo.orchestrator.exception.CheckoutRejectedException;
import com.demo.orchestrator.exception.CheckoutUnavailableException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The saga, spelled out as a single readable method: each step and its
 * compensation live right next to each other. This is the tradeoff of
 * orchestration over choreography - one service (this one) has to know the
 * whole recipe, but you can read top to bottom and see exactly what happens
 * when step N fails after steps 1..N-1 already succeeded.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final ResilientProductClient productClient;
    private final OrderClient orderClient;
    private final ResilientPaymentClient paymentClient;
    private final OrderEventPublisher eventPublisher;

    public CheckoutService(ResilientProductClient productClient, OrderClient orderClient,
                            ResilientPaymentClient paymentClient, OrderEventPublisher eventPublisher) {
        this.productClient = productClient;
        this.orderClient = orderClient;
        this.paymentClient = paymentClient;
        this.eventPublisher = eventPublisher;
    }

    public OrderDto checkout(Long userId, CheckoutRequest request) {
        log.info("Starting checkout: user={} product={} qty={}", userId, request.productId(), request.quantity());

        // --- Step 1: reserve stock ---------------------------------------
        ProductDto product = reserveStock(request);

        // --- Step 2: create the order record as PENDING ------------------
        OrderDto order = createOrder(userId, request, product);

        // --- Step 3: charge payment ---------------------------------------
        // payment-service is idempotent in orderId, so the retries behind
        // paymentClient.charge() can safely repeat a call that may already
        // have gone through - a replay returns the original payment rather
        // than charging the order a second time.
        //
        // 402 Payment Required has no dedicated FeignException subclass (only
        // the more common statuses do), so it's distinguished by status code.
        PaymentDto payment;
        try {
            payment = paymentClient.charge(new ChargeRequest(order.id(), userId, order.totalPrice()));
        } catch (FeignException ex) {
            compensateStock(request);
            if (ex.status() == 402) {
                failOrder(order.id(), "Payment declined");
                throw new CheckoutRejectedException("Payment declined for order " + order.id());
            }
            // 409: the order already has a payment that this charge isn't a
            // replay of (refunded, or a different amount). Deterministic, so
            // it's a rejection the caller shouldn't retry - not an outage.
            if (ex.status() == 409) {
                failOrder(order.id(), "Order already has a conflicting payment");
                throw new CheckoutRejectedException("Order " + order.id() + " already has a conflicting payment");
            }
            failOrder(order.id(), "Payment could not be processed: " + ex.getMessage());
            throw new CheckoutUnavailableException("payment-service is unavailable", ex);
        } catch (Exception ex) {
            compensateStock(request);
            failOrder(order.id(), "Payment could not be processed: " + ex.getMessage());
            throw new CheckoutUnavailableException("payment-service is unavailable", ex);
        }

        // --- Step 4: confirm the order -------------------------------------
        OrderDto confirmed;
        try {
            confirmed = orderClient.confirmOrder(order.id());
        } catch (Exception ex) {
            // Payment already succeeded at this point, so compensating means
            // undoing *two* prior steps: give the stock back AND refund. The
            // order itself is also marked FAILED (best effort - if
            // order-service is what's unreachable, this call fails too, and
            // the order is left stuck PENDING for manual reconciliation).
            compensateStock(request);
            compensatePayment(payment.id(), order.id());
            failOrder(order.id(), "Order could not be confirmed after payment succeeded");
            throw new CheckoutUnavailableException(
                    "order " + order.id() + " could not be confirmed after payment succeeded; compensated", ex);
        }

        eventPublisher.publish(OrderEvent.confirmed(confirmed.id(), userId, confirmed.productName(),
                confirmed.quantity(), confirmed.totalPrice()));
        log.info("Checkout succeeded: order={} user={}", confirmed.id(), userId);
        return confirmed;
    }

    private ProductDto reserveStock(CheckoutRequest request) {
        try {
            return productClient.reserveStock(request.productId(), request.quantity());
        } catch (FeignException.NotFound ex) {
            throw new CheckoutRejectedException("No such product: " + request.productId());
        } catch (FeignException.Conflict ex) {
            throw new CheckoutRejectedException("Insufficient stock for product " + request.productId());
        } catch (Exception ex) {
            // Nothing succeeded yet, so there's nothing to compensate.
            throw new CheckoutUnavailableException("product-service is unavailable", ex);
        }
    }

    private OrderDto createOrder(Long userId, CheckoutRequest request, ProductDto product) {
        try {
            return orderClient.createOrder(new CreateOrderRequest(
                    userId, product.id(), product.name(), request.quantity(), product.price()));
        } catch (Exception ex) {
            compensateStock(request);
            throw new CheckoutUnavailableException("order-service is unavailable", ex);
        }
    }

    private void compensateStock(CheckoutRequest request) {
        try {
            productClient.releaseStock(request.productId(), request.quantity());
        } catch (Exception ex) {
            log.error("COMPENSATION FAILED: could not release {} units of product {} - manual reconciliation needed",
                    request.quantity(), request.productId(), ex);
        }
    }

    private void compensatePayment(Long paymentId, Long orderId) {
        try {
            paymentClient.refund(paymentId);
        } catch (Exception ex) {
            log.error("COMPENSATION FAILED: could not refund payment {} for order {} - manual reconciliation needed",
                    paymentId, orderId, ex);
        }
    }

    private void failOrder(Long orderId, String reason) {
        try {
            OrderDto failed = orderClient.failOrder(orderId, new FailOrderRequest(reason));
            eventPublisher.publish(OrderEvent.failed(failed.id(), failed.userId(), failed.productName(),
                    failed.quantity(), reason));
        } catch (Exception ex) {
            log.error("Could not mark order {} as FAILED ({}), and could not publish OrderFailed event", orderId, reason, ex);
        }
    }
}
