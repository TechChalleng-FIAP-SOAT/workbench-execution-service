package com.workbench.workshop.application.product;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lista o catálogo de peças e insumos. */
@Service
@RequiredArgsConstructor
public class ListProductsUseCase {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Product> execute() {
        return productRepository.findAll();
    }
}
