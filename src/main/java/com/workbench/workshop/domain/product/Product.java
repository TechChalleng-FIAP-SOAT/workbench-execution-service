package com.workbench.workshop.domain.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Produto (peça ou insumo). No Execution Service (MongoDB) é um documento único
 * com {@code type} discriminando PART/SUPPLY, em vez da hierarquia table-per-subtype
 * do monólito — modelagem natural para documento.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    private String id;
    private String name;
    private String sku;
    private ProductType type;
    private UnitOfMeasure unit;
    private String category;
    private String brand;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private boolean active = true;

    // Campos específicos de PART (nulos para SUPPLY).
    private String manufacturerCode;
    private Integer warrantyMonths;

    // Campos específicos de SUPPLY (nulos para PART).
    private Boolean fractionalAllowed;
    private BigDecimal packageSize;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
