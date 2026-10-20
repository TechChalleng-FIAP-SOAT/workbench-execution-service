package com.workbench.workshop.adapter.in.web;

import com.workbench.workshop.domain.stock.Stock;
import com.workbench.workshop.domain.stock.StockReservation;
import com.workbench.workshop.domain.stock.StockReservationStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** DTOs de request/response do estoque. */
public final class StockDtos {
    private StockDtos() {}

    public record CreateStockRequest(String productId, BigDecimal quantity, BigDecimal minimumQuantity) {}
    public record MovementRequest(BigDecimal quantity, String reason) {}

    public record Item(String productId, BigDecimal quantity) {}
    public record AvailabilityRequest(List<Item> items) {}
    public record ReserveRequest(UUID serviceOrderId, List<Item> items) {}

    public record StockResponse(
            String id, String productId, BigDecimal quantity, BigDecimal reservedQuantity,
            BigDecimal availableQuantity, BigDecimal minimumQuantity, boolean lowStock
    ) {
        public static StockResponse from(Stock s) {
            return new StockResponse(s.getId(), s.getProductId(), s.getQuantity(), s.getReservedQuantity(),
                    s.getAvailableQuantity(), s.getMinimumQuantity(), s.isLowStock());
        }
    }

    public record ReservationResponse(
            String id, String productId, UUID serviceOrderId, BigDecimal quantity, StockReservationStatus status
    ) {
        public static ReservationResponse from(StockReservation r) {
            return new ReservationResponse(r.getId(), r.getProductId(), r.getServiceOrderId(),
                    r.getQuantity(), r.getStatus());
        }
    }
}
