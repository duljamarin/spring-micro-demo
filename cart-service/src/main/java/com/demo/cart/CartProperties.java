package com.demo.cart;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cart")
public class CartProperties {

    /**
     * Wall-clock budget for one cart checkout. Items are run through the
     * saga one at a time, so without a cap the total is unbounded in cart
     * size and the gateway would eventually cut the call off mid-saga - with
     * the user seeing a timeout while orders quietly keep being placed
     * behind it. Once the budget is spent, remaining items aren't started.
     */
    private Duration checkoutBudget = Duration.ofSeconds(20);

    public Duration getCheckoutBudget() {
        return checkoutBudget;
    }

    public void setCheckoutBudget(Duration checkoutBudget) {
        this.checkoutBudget = checkoutBudget;
    }
}
