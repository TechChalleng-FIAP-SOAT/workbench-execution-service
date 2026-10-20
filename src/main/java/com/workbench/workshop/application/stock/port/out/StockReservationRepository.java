package com.workbench.workshop.application.stock.port.out;

import com.workbench.workshop.domain.stock.StockReservation;
import com.workbench.workshop.domain.stock.StockReservationStatus;

import java.util.List;
import java.util.UUID;

public interface StockReservationRepository {
    StockReservation save(StockReservation reservation);
    List<StockReservation> findByServiceOrderId(UUID serviceOrderId);
    List<StockReservation> findByServiceOrderIdAndStatus(UUID serviceOrderId, StockReservationStatus status);
}
