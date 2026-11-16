package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Estoque de um produto (404 se não existir). */
@Service
@RequiredArgsConstructor
public class FindStockByProductUseCase {

    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public Stock execute(String productId) {
        return stockRepository.getByProductId(productId);
    }
}
