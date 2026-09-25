package com.demo.orchestrator.client;

import feign.FeignException;

import java.util.function.Predicate;

/**
 * Statuses that are payment-service answering properly with an outcome the
 * saga has to respect, not payment-service being unhealthy: 402 (declined)
 * and 409 (the order already has a payment this charge isn't a replay of).
 * Both are deterministic, so retrying only repeats them and counting them as
 * failures would trip the circuit breaker on perfectly healthy traffic.
 *
 * <p>This is a predicate rather than a resilience4j "ignore-exceptions" class
 * list because 402 has no dedicated FeignException subclass (unlike 404 or
 * 409), so it can't be named directly the way NotFound/Conflict are for
 * productService. Referenced from order-orchestrator.yml as
 * resilience4j.circuitbreaker.instances.paymentService.ignore-exception-predicate.
 */
public class PaymentBusinessOutcomePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return throwable instanceof FeignException fe && (fe.status() == 402 || fe.status() == 409);
    }
}
