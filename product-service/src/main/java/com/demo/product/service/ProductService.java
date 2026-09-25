package com.demo.product.service;

import com.demo.product.dto.ProductRequest;
import com.demo.product.exception.InsufficientStockException;
import com.demo.product.exception.ProductNotFoundException;
import com.demo.product.model.Product;
import com.demo.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product create(ProductRequest request) {
        Product product = new Product(request.name(), request.description(), request.price(), request.quantity());
        return productRepository.save(product);
    }

    /**
     * Decrements stock atomically for the calling order-orchestrator.
     * Optimistic locking (see Product.version) causes a retry-able failure
     * instead of a silently lost update if two orders race for the last units.
     */
    @Transactional
    public Product reserveStock(Long id, int quantity) {
        Product product = findById(id);
        if (product.getQuantity() < quantity) {
            throw new InsufficientStockException(id, quantity, product.getQuantity());
        }
        product.setQuantity(product.getQuantity() - quantity);
        return productRepository.save(product);
    }

    /**
     * Saga compensation for reserveStock: puts the quantity back when a later
     * step (payment, order confirmation) fails after stock was already
     * reserved. Called only by order-orchestrator.
     */
    @Transactional
    public Product releaseStock(Long id, int quantity) {
        Product product = findById(id);
        product.setQuantity(product.getQuantity() + quantity);
        return productRepository.save(product);
    }

    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }
}
