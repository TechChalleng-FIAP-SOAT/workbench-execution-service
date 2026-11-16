package com.workbench.workshop.application.product;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adiciona peça/insumo ao catálogo (sempre ativo). */
@Service
@RequiredArgsConstructor
public class CreateProductUseCase {

    private final ProductRepository productRepository;

    @Transactional
    public Product execute(Product product) {
        if (productRepository.findBySku(product.getSku()).isPresent()) {
            throw new IllegalStateException("Já existe produto com o SKU " + product.getSku() + ".");
        }
        product.activate();
        return productRepository.save(product);
    }
}
