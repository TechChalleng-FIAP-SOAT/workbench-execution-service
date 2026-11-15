package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockMovementRepository;
import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.domain.stock.Stock;
import com.workbench.workshop.domain.stock.StockMovementType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Entrada de estoque (compra/reposição), com registro do movimento.
 * Transacional: compete com a reserva da Saga pelo mesmo documento de estoque, e o
 * MongoDB aborta a 2ª gravação concorrente (WriteConflict) em vez de sobrescrever.
 */
@Service
@RequiredArgsConstructor
public class RegisterStockEntryUseCase {

    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;

    @Transactional
    public Stock execute(String productId, BigDecimal quantity, String reason) {
        Stock stock = stockRepository.getByProductId(productId);
        stock.addQuantity(quantity);
        Stock saved = stockRepository.save(stock);
        movementRepository.record(saved.getId(), StockMovementType.ENTRY, quantity, reason);
        return saved;
    }
}
