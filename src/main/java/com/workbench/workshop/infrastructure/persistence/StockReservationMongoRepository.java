package com.workbench.workshop.infrastructure.persistence;

import com.workbench.workshop.domain.stock.StockReservationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface StockReservationMongoRepository extends MongoRepository<StockReservationDocument, String> {
    List<StockReservationDocument> findByServiceOrderId(UUID serviceOrderId);
    List<StockReservationDocument> findByServiceOrderIdAndStatus(UUID serviceOrderId, StockReservationStatus status);
}
