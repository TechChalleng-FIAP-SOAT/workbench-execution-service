package com.workbench.workshop.application.product;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atualiza o cadastro de uma peça/insumo (preço, marca, dados de PART/SUPPLY…). O SKU
 * pode mudar, desde que não seja o de outro produto. Peças já reservadas mantêm o preço
 * congelado no orçamento — o snapshot é feito na reserva.
 */
@Service
@RequiredArgsConstructor
public class UpdateProductUseCase {

    private final ProductRepository productRepository;

    @Transactional
    public Product execute(String id, Product changes) {
        Product product = productRepository.getById(id);
        productRepository.findBySku(changes.getSku())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalStateException("Já existe produto com o SKU " + changes.getSku() + ".");
                });
        product.replaceDetailsWith(changes);
        return productRepository.save(product);
    }
}
