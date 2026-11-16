package com.workbench.workshop.application.product.port.out;

import com.workbench.workshop.domain.product.Product;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(String id);
    Optional<Product> findBySku(String sku);
    List<Product> findAll();
    void deleteById(String id);

    /** Produto pelo id ou {@link NoSuchElementException} (404). */
    default Product getById(String id) {
        return findById(id).orElseThrow(() -> new NoSuchElementException("Produto não encontrado: " + id));
    }
}
