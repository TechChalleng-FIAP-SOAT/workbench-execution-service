package com.workbench.workshop.domain.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {
    private String id;
    private String stockId;
    private StockMovementType type;
    private BigDecimal quantity;
    private String reason;
    private LocalDateTime createdAt;
}
