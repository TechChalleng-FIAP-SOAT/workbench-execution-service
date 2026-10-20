package com.workbench.workshop.application.stock.port.out;

import com.workbench.workshop.domain.stock.Stock;

import java.util.List;
import java.util.Optional;

public interface StockRepository {
    Stock save(Stock stock);
    Optional<Stock> findById(String id);
    Optional<Stock> findByProductId(String productId);
    List<Stock> findAll();
    List<Stock> findLowStock();
}
