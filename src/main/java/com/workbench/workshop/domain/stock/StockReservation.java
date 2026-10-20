package com.workbench.workshop.domain.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservation {
    private String id;
    private String stockId;
    private String productId;
    private UUID serviceOrderId;
    private BigDecimal quantity;
    private StockReservationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
