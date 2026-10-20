package com.workbench.workshop.adapter.out.persistence;

import com.workbench.workshop.application.stock.port.out.StockReservationRepository;
import com.workbench.workshop.domain.stock.StockReservation;
import com.workbench.workshop.domain.stock.StockReservationStatus;
import com.workbench.workshop.infrastructure.persistence.StockReservationDocument;
import com.workbench.workshop.infrastructure.persistence.StockReservationMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StockReservationPersistenceAdapter implements StockReservationRepository {

    private final StockReservationMongoRepository repository;

    @Override
    public StockReservation save(StockReservation r) {
        LocalDateTime now = LocalDateTime.now();
        StockReservationDocument doc = StockReservationDocument.builder()
                .id(r.getId())
                .stockId(r.getStockId())
                .productId(r.getProductId())
                .serviceOrderId(r.getServiceOrderId())
                .quantity(r.getQuantity())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt() : now)
                .updatedAt(now)
                .build();
        return toDomain(repository.save(doc));
    }

    @Override
    public List<StockReservation> findByServiceOrderId(UUID serviceOrderId) {
        return repository.findByServiceOrderId(serviceOrderId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<StockReservation> findByServiceOrderIdAndStatus(UUID serviceOrderId, StockReservationStatus status) {
        return repository.findByServiceOrderIdAndStatus(serviceOrderId, status).stream().map(this::toDomain).toList();
    }

    private StockReservation toDomain(StockReservationDocument d) {
        return StockReservation.builder()
                .id(d.getId())
                .stockId(d.getStockId())
                .productId(d.getProductId())
                .serviceOrderId(d.getServiceOrderId())
                .quantity(d.getQuantity())
                .status(d.getStatus())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
