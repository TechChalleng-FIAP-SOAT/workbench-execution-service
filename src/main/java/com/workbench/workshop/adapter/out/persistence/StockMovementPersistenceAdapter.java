package com.workbench.workshop.adapter.out.persistence;

import com.workbench.workshop.application.stock.port.out.StockMovementRepository;
import com.workbench.workshop.domain.stock.StockMovement;
import com.workbench.workshop.infrastructure.persistence.StockMovementDocument;
import com.workbench.workshop.infrastructure.persistence.StockMovementMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StockMovementPersistenceAdapter implements StockMovementRepository {

    private final StockMovementMongoRepository repository;

    @Override
    public StockMovement save(StockMovement m) {
        StockMovementDocument doc = StockMovementDocument.builder()
                .id(m.getId())
                .stockId(m.getStockId())
                .type(m.getType())
                .quantity(m.getQuantity())
                .reason(m.getReason())
                .createdAt(m.getCreatedAt() != null ? m.getCreatedAt() : LocalDateTime.now())
                .build();
        return toDomain(repository.save(doc));
    }

    @Override
    public List<StockMovement> findByStockId(String stockId) {
        return repository.findByStockId(stockId).stream().map(this::toDomain).toList();
    }

    private StockMovement toDomain(StockMovementDocument d) {
        return StockMovement.builder()
                .id(d.getId()).stockId(d.getStockId()).type(d.getType())
                .quantity(d.getQuantity()).reason(d.getReason()).createdAt(d.getCreatedAt())
                .build();
    }
}
