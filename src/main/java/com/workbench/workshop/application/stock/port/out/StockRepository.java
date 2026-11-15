package com.workbench.workshop.application.stock.port.out;

import com.workbench.workshop.domain.stock.Stock;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public interface StockRepository {
    Stock save(Stock stock);
    Optional<Stock> findById(String id);
    Optional<Stock> findByProductId(String productId);
    List<Stock> findAll();
    List<Stock> findLowStock();

    /** Estoque do produto ou {@link NoSuchElementException} (404). */
    default Stock getByProductId(String productId) {
        return findByProductId(productId)
                .orElseThrow(() -> new NoSuchElementException("Estoque não encontrado para o produto: " + productId));
    }
}
