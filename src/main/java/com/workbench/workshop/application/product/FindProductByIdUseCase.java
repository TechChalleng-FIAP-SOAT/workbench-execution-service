package com.workbench.workshop.application.product;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Produto do catálogo pelo id (404 se não existir). */
@Service
@RequiredArgsConstructor
public class FindProductByIdUseCase {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Product execute(String id) {
        return productRepository.getById(id);
    }
}
