package com.workbench.workshop.adapter.out.persistence;

import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.domain.stock.Stock;
import com.workbench.workshop.infrastructure.persistence.StockDocument;
import com.workbench.workshop.infrastructure.persistence.StockMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StockPersistenceAdapter implements StockRepository {

    private final StockMongoRepository repository;

    @Override
    public Stock save(Stock stock) {
        LocalDateTime now = LocalDateTime.now();
        StockDocument doc = StockDocument.builder()
                .id(stock.getId())
                .productId(stock.getProductId())
                .quantity(stock.getQuantity())
                .reservedQuantity(stock.getReservedQuantity())
                .availableQuantity(stock.getAvailableQuantity())
                .minimumQuantity(stock.getMinimumQuantity())
                .createdAt(stock.getCreatedAt() != null ? stock.getCreatedAt() : now)
                .updatedAt(now)
                .build();
        return toDomain(repository.save(doc));
    }

    @Override
    public Optional<Stock> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Stock> findByProductId(String productId) {
        return repository.findByProductId(productId).map(this::toDomain);
    }

    @Override
    public List<Stock> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Stock> findLowStock() {
        return repository.findAll().stream()
                .map(this::toDomain)
                .filter(Stock::isLowStock)
                .toList();
    }

    private Stock toDomain(StockDocument d) {
        return Stock.builder()
                .id(d.getId())
                .productId(d.getProductId())
                .quantity(d.getQuantity())
                .reservedQuantity(d.getReservedQuantity())
                .availableQuantity(d.getAvailableQuantity())
                .minimumQuantity(d.getMinimumQuantity())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
