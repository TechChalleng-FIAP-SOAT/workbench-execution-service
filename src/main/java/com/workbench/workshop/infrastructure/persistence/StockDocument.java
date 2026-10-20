package com.workbench.workshop.infrastructure.persistence;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "stock")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockDocument {
    @Id
    private String id;
    @Indexed(unique = true)
    private String productId;
    private BigDecimal quantity;
    private BigDecimal reservedQuantity;
    private BigDecimal availableQuantity;
    private BigDecimal minimumQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
