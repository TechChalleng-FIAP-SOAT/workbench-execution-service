package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockMovementRepository;
import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.domain.stock.Stock;
import com.workbench.workshop.domain.stock.StockMovement;
import com.workbench.workshop.domain.stock.StockMovementType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Casos de uso de estoque (CRUD + entrada/saída), portados do monólito.
 * Agrupados numa fachada para reduzir boilerplate; cada método é um caso de uso.
 */
@Service
@RequiredArgsConstructor
public class StockUseCases {

    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;

    public Stock create(String productId, BigDecimal quantity, BigDecimal minimumQuantity) {
        Stock stock = Stock.builder()
                .productId(productId)
                .quantity(quantity == null ? BigDecimal.ZERO : quantity)
                .reservedQuantity(BigDecimal.ZERO)
                .minimumQuantity(minimumQuantity == null ? BigDecimal.ZERO : minimumQuantity)
                .build();
        stock.recalculateAvailableQuantity();
        return stockRepository.save(stock);
    }

    public Stock findByProductId(String productId) {
        return stockRepository.findByProductId(productId)
                .orElseThrow(() -> new NoSuchElementException("Stock not found for product: " + productId));
    }

    public List<Stock> findAll() {
        return stockRepository.findAll();
    }

    public List<Stock> findLowStock() {
        return stockRepository.findLowStock();
    }

    public Stock entry(String productId, BigDecimal quantity, String reason) {
        Stock stock = findByProductId(productId);
        stock.addQuantity(quantity);
        Stock saved = stockRepository.save(stock);
        recordMovement(saved.getId(), StockMovementType.ENTRY, quantity, reason);
        return saved;
    }

    public Stock exit(String productId, BigDecimal quantity, String reason) {
        Stock stock = findByProductId(productId);
        stock.removeQuantity(quantity);
        Stock saved = stockRepository.save(stock);
        recordMovement(saved.getId(), StockMovementType.EXIT, quantity, reason);
        return saved;
    }

    public Stock updateMinimum(String productId, BigDecimal minimumQuantity) {
        Stock stock = findByProductId(productId);
        stock.setMinimumQuantity(minimumQuantity);
        stock.recalculateAvailableQuantity();
        return stockRepository.save(stock);
    }

    public List<StockMovement> findMovements(String productId) {
        Stock stock = findByProductId(productId);
        return movementRepository.findByStockId(stock.getId());
    }

    private void recordMovement(String stockId, StockMovementType type, BigDecimal quantity, String reason) {
        movementRepository.save(StockMovement.builder()
                .stockId(stockId).type(type).quantity(quantity).reason(reason).build());
    }
}
