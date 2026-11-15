package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Produtos com disponível no mínimo ou abaixo dele (alerta de reposição). */
@Service
@RequiredArgsConstructor
public class ListLowStockUseCase {

    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public List<Stock> execute() {
        return stockRepository.findLowStock();
    }
}
