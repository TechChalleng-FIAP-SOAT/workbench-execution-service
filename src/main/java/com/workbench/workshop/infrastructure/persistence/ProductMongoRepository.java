package com.workbench.workshop.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductMongoRepository extends MongoRepository<ProductDocument, String> {
    Optional<ProductDocument> findBySku(String sku);
}
