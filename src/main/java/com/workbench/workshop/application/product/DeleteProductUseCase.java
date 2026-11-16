package com.workbench.workshop.application.product;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Remove peça/insumo do catálogo (404 se não existir). */
@Service
@RequiredArgsConstructor
public class DeleteProductUseCase {

    private final ProductRepository productRepository;

    @Transactional
    public void execute(String id) {
        productRepository.getById(id); // garante 404 se não existir
        productRepository.deleteById(id);
    }
}
