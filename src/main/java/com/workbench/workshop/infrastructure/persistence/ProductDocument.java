package com.workbench.workshop.infrastructure.persistence;

import com.workbench.workshop.domain.product.ProductType;
import com.workbench.workshop.domain.product.UnitOfMeasure;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "product")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductDocument {
    @Id
    private String id;
    private String name;
    @Indexed(unique = true)
    private String sku;
    private ProductType type;
    private UnitOfMeasure unit;
    private String category;
    private String brand;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private boolean active;
    private String manufacturerCode;
    private Integer warrantyMonths;
    private Boolean fractionalAllowed;
    private BigDecimal packageSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
