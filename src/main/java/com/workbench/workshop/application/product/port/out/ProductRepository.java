package com.workbench.workshop.application.product.port.out;

import com.workbench.workshop.domain.product.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(String id);
    Optional<Product> findBySku(String sku);
    List<Product> findAll();
    void deleteById(String id);
}
