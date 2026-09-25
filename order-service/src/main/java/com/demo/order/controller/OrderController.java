package com.demo.order.controller;

import com.demo.order.dto.CreateOrderRequest;
import com.demo.order.dto.FailOrderRequest;
import com.demo.order.dto.OrderResponse;
import com.demo.order.model.Order;
import com.demo.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> getAll(@RequestParam(required = false) Long userId) {
        List<Order> orders = userId != null ? orderService.findByUserId(userId) : orderService.findAll();
        return orders.stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable Long id) {
        return OrderResponse.from(orderService.findById(id));
    }

    /** Called only by order-orchestrator, after it has already reserved stock for this order. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return OrderResponse.from(orderService.create(request));
    }

    @PatchMapping("/{id}/confirm")
    public OrderResponse confirm(@PathVariable Long id) {
        return OrderResponse.from(orderService.confirm(id));
    }

    @PatchMapping("/{id}/fail")
    public OrderResponse fail(@PathVariable Long id, @Valid @RequestBody FailOrderRequest request) {
        return OrderResponse.from(orderService.fail(id, request.reason()));
    }
}
