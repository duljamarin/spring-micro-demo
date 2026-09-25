package com.demo.order.service;

import com.demo.order.dto.CreateOrderRequest;
import com.demo.order.exception.OrderNotFoundException;
import com.demo.order.model.Order;
import com.demo.order.model.OrderStatus;
import com.demo.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pure data owner for orders - no calls to any other service. The saga that
 * decides whether an order should exist, be confirmed, or fail lives in
 * order-orchestrator; this class just persists whatever it's told.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order create(CreateOrderRequest request) {
        Order order = new Order(
                request.userId(),
                request.productId(),
                request.productName(),
                request.quantity(),
                request.unitPrice()
        );
        return orderRepository.save(order);
    }

    public Order confirm(Long id) {
        Order order = findById(id);
        order.setStatus(OrderStatus.CONFIRMED);
        return orderRepository.save(order);
    }

    public Order fail(Long id, String reason) {
        Order order = findById(id);
        order.setStatus(OrderStatus.FAILED);
        order.setFailureReason(reason != null && reason.length() > 1000 ? reason.substring(0, 1000) : reason);
        return orderRepository.save(order);
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public List<Order> findByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public Order findById(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }
}
