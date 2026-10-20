package com.workbench.workshop.application.product;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/** Casos de uso de produto (adicionar/remover peça — requisito de API do PDF). */
@Service
@RequiredArgsConstructor
public class ProductUseCases {

    private final ProductRepository productRepository;

    public Product create(Product product) {
        product.setActive(true);
        return productRepository.save(product);
    }

    public Product findById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public void delete(String id) {
        findById(id); // garante 404 se não existir
        productRepository.deleteById(id);
    }
}
