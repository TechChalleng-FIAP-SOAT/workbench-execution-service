package com.workbench.workshop.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockMongoRepository extends MongoRepository<StockDocument, String> {
    Optional<StockDocument> findByProductId(String productId);

    /** Estoque disponível no mínimo ou abaixo dele, comparado no banco (campos Decimal128). */
    @Query("{ $expr: { $lte: ['$availableQuantity', '$minimumQuantity'] } }")
    List<StockDocument> findLowStock();
}
