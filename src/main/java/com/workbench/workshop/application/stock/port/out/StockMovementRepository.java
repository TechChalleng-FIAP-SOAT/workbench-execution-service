package com.workbench.workshop.application.stock.port.out;

import com.workbench.workshop.domain.stock.StockMovement;

import java.util.List;

public interface StockMovementRepository {
    StockMovement save(StockMovement movement);
    List<StockMovement> findByStockId(String stockId);
}
