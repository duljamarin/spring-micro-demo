package com.demo.orchestrator.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service")
public interface ProductClient {

    @PostMapping("/api/products/{id}/reserve")
    ProductDto reserveStock(@PathVariable("id") Long id, @RequestBody StockQuantityRequest request);

    @PostMapping("/api/products/{id}/release")
    ProductDto releaseStock(@PathVariable("id") Long id, @RequestBody StockQuantityRequest request);
}
