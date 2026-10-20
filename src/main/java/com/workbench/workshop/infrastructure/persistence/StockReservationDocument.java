package com.workbench.workshop.infrastructure.persistence;

import com.workbench.workshop.domain.stock.StockReservationStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "stock_reservation")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockReservationDocument {
    @Id
    private String id;
    private String stockId;
    private String productId;
    @Indexed
    private UUID serviceOrderId;
    private BigDecimal quantity;
    private StockReservationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
