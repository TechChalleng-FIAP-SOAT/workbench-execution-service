package com.workbench.workshop.application.stock.port.out;

import com.workbench.workshop.domain.stock.StockMovement;
import com.workbench.workshop.domain.stock.StockMovementType;

import java.math.BigDecimal;
import java.util.List;

public interface StockMovementRepository {
    StockMovement save(StockMovement movement);
    List<StockMovement> findByStockId(String stockId);

    /** Registra um movimento de estoque (trilha de auditoria de entradas, saídas e reservas). */
    default void record(String stockId, StockMovementType type, BigDecimal quantity, String reason) {
        save(StockMovement.builder().stockId(stockId).type(type).quantity(quantity).reason(reason).build());
    }
}
