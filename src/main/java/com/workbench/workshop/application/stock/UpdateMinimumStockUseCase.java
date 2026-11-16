package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Altera a quantidade mínima (alerta de estoque baixo) de um produto.
 * Transacional: compete com a reserva da Saga pelo mesmo documento de estoque, e o
 * MongoDB aborta a 2ª gravação concorrente (WriteConflict) em vez de sobrescrever.
 */
@Service
@RequiredArgsConstructor
public class UpdateMinimumStockUseCase {

    private final StockRepository stockRepository;

    @Transactional
    public Stock execute(String productId, BigDecimal minimumQuantity) {
        Stock stock = stockRepository.getByProductId(productId);
        stock.changeMinimum(minimumQuantity);
        return stockRepository.save(stock);
    }
}
