package com.demo.product;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from the "inventory.*" keys served by the config server, demonstrating
 * externalized, centrally managed business configuration (see product-service.yml
 * in the config repo).
 */
@ConfigurationProperties(prefix = "inventory")
public class InventoryProperties {

    private int lowStockThreshold = 5;

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }
}
