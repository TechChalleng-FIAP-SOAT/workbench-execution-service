package com.workbench.workshop.infrastructure.persistence;

import com.workbench.workshop.domain.stock.StockMovementType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "stock_movement")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockMovementDocument {
    @Id
    private String id;
    @Indexed
    private String stockId;
    private StockMovementType type;
    private BigDecimal quantity;
    private String reason;
    private LocalDateTime createdAt;
}
