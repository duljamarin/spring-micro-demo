package com.demo.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private BigDecimal declineAboveAmount = new BigDecimal("1000.00");

    public BigDecimal getDeclineAboveAmount() {
        return declineAboveAmount;
    }

    public void setDeclineAboveAmount(BigDecimal declineAboveAmount) {
        this.declineAboveAmount = declineAboveAmount;
    }
}
