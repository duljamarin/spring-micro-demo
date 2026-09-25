package com.demo.product.controller;

import com.demo.product.dto.ProductRequest;
import com.demo.product.dto.ProductResponse;
import com.demo.product.dto.ReserveStockRequest;
import com.demo.product.model.Product;
import com.demo.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> getAll() {
        return productService.findAll().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return ProductResponse.from(productService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        Product created = productService.create(request);
        return ProductResponse.from(created);
    }

    /**
     * Called by order-orchestrator (never direct DB access) to reserve stock
     * as the first step of the checkout saga. Keeps inventory as the single
     * source of truth inside the service that owns it.
     */
    @PostMapping("/{id}/reserve")
    public ProductResponse reserve(@PathVariable Long id, @Valid @RequestBody ReserveStockRequest request) {
        Product updated = productService.reserveStock(id, request.quantity());
        return ProductResponse.from(updated);
    }

    /** Compensating action: called by order-orchestrator when a later saga step fails. */
    @PostMapping("/{id}/release")
    public ProductResponse release(@PathVariable Long id, @Valid @RequestBody ReserveStockRequest request) {
        Product updated = productService.releaseStock(id, request.quantity());
        return ProductResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
