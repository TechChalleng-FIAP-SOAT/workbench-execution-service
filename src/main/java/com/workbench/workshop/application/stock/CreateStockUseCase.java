package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Cria o registro de estoque de um produto (quantidade e mínimo; nulo = zero).
 * Transacional: compete com a reserva da Saga pelo mesmo documento de estoque, e o
 * MongoDB aborta a 2ª gravação concorrente (WriteConflict) em vez de sobrescrever.
 */
@Service
@RequiredArgsConstructor
public class CreateStockUseCase {

    private final StockRepository stockRepository;

    @Transactional
    public Stock execute(String productId, BigDecimal quantity, BigDecimal minimumQuantity) {
        Stock stock = Stock.builder()
                .productId(productId)
                .quantity(quantity == null ? BigDecimal.ZERO : quantity)
                .reservedQuantity(BigDecimal.ZERO)
                .minimumQuantity(minimumQuantity == null ? BigDecimal.ZERO : minimumQuantity)
                .build();
        stock.recalculateAvailableQuantity();
        return stockRepository.save(stock);
    }
}
