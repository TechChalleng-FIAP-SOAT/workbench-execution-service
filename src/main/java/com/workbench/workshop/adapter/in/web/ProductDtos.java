package com.workbench.workshop.adapter.in.web;

import com.workbench.workshop.domain.product.Product;
import com.workbench.workshop.domain.product.ProductType;
import com.workbench.workshop.domain.product.UnitOfMeasure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTOs de entrada/saída do catálogo de produtos. */
public final class ProductDtos {

    private ProductDtos() {}

    /** Dados de criação e de atualização — evita expor o agregado de domínio na borda HTTP. */
    public record ProductRequest(
            @NotBlank String name, @NotBlank String sku, @NotNull ProductType type, @NotNull UnitOfMeasure unit,
            String category, String brand,
            @PositiveOrZero BigDecimal costPrice, @NotNull @PositiveOrZero BigDecimal salePrice,
            String manufacturerCode, Integer warrantyMonths,
            Boolean fractionalAllowed, BigDecimal packageSize
    ) {
        Product toProduct() {
            return Product.builder()
                    .name(name).sku(sku).type(type).unit(unit)
                    .category(category).brand(brand)
                    .costPrice(costPrice).salePrice(salePrice)
                    .manufacturerCode(manufacturerCode).warrantyMonths(warrantyMonths)
                    .fractionalAllowed(fractionalAllowed).packageSize(packageSize)
                    .build();
        }
    }

    /** DTO de resposta — a borda HTTP não serializa o agregado de domínio diretamente. */
    public record ProductResponse(
            String id, String name, String sku, ProductType type, UnitOfMeasure unit,
            String category, String brand, BigDecimal costPrice, BigDecimal salePrice, boolean active,
            String manufacturerCode, Integer warrantyMonths, Boolean fractionalAllowed, BigDecimal packageSize,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getSku(), p.getType(), p.getUnit(),
                    p.getCategory(), p.getBrand(), p.getCostPrice(), p.getSalePrice(), p.isActive(),
                    p.getManufacturerCode(), p.getWarrantyMonths(), p.getFractionalAllowed(), p.getPackageSize(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
