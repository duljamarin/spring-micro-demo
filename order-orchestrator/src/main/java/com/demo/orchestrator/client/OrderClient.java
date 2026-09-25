package com.demo.orchestrator.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service")
public interface OrderClient {

    @PostMapping("/api/orders")
    OrderDto createOrder(@RequestBody CreateOrderRequest request);

    @PatchMapping("/api/orders/{id}/confirm")
    OrderDto confirmOrder(@PathVariable("id") Long id);

    @PatchMapping("/api/orders/{id}/fail")
    OrderDto failOrder(@PathVariable("id") Long id, @RequestBody FailOrderRequest request);
}
