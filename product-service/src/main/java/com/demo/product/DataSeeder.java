package com.demo.product;

import com.demo.product.model.Product;
import com.demo.product.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    public DataSeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            productRepository.save(new Product("Mechanical Keyboard", "Hot-swappable, brown switches", new BigDecimal("89.99"), 25));
            productRepository.save(new Product("27-inch Monitor", "1440p IPS, 144Hz", new BigDecimal("299.00"), 10));
            productRepository.save(new Product("USB-C Dock", "10-in-1 hub", new BigDecimal("59.50"), 3));
        }
    }
}
