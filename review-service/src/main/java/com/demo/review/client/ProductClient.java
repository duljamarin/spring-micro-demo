package com.demo.review.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Read-only view of the catalog. review-service stores productId and nothing
 * else about a product - the name shown on the scoreboard is fetched from the
 * service that owns it, resolved through Eureka by service id.
 */
@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/products")
    List<ProductDto> findAll();

    @GetMapping("/api/products/{id}")
    ProductDto findById(@PathVariable("id") Long id);
}
