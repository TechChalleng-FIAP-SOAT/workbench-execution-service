package com.workbench.workshop.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface StockMongoRepository extends MongoRepository<StockDocument, String> {
    Optional<StockDocument> findByProductId(String productId);
}
