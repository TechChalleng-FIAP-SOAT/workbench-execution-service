package com.workbench.workshop.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StockMovementMongoRepository extends MongoRepository<StockMovementDocument, String> {
    List<StockMovementDocument> findByStockId(String stockId);
}
