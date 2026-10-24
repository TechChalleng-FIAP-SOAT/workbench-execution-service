package com.workbench.workshop.adapter.in.web;

import com.workbench.workshop.application.product.ProductUseCases;
import com.workbench.workshop.domain.product.Product;
import com.workbench.workshop.domain.product.ProductType;
import com.workbench.workshop.domain.product.UnitOfMeasure;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product", description = "Cadastro de peças e insumos")
public class ProductController {

    private final ProductUseCases productUseCases;

    /** DTO de criação — evita expor o agregado de domínio na borda HTTP. */
    public record CreateProductRequest(
            @NotBlank String name, @NotBlank String sku, @NotNull ProductType type, @NotNull UnitOfMeasure unit,
            String category, String brand,
            @PositiveOrZero BigDecimal costPrice, @NotNull @PositiveOrZero BigDecimal salePrice,
            String manufacturerCode, Integer warrantyMonths,
            Boolean fractionalAllowed, BigDecimal packageSize
    ) {}

    @PostMapping
    @Operation(summary = "Adicionar peça/insumo ao catálogo")
    public ResponseEntity<Product> create(@Valid @RequestBody CreateProductRequest req) {
        Product product = Product.builder()
                .name(req.name()).sku(req.sku()).type(req.type()).unit(req.unit())
                .category(req.category()).brand(req.brand())
                .costPrice(req.costPrice()).salePrice(req.salePrice())
                .manufacturerCode(req.manufacturerCode()).warrantyMonths(req.warrantyMonths())
                .fractionalAllowed(req.fractionalAllowed()).packageSize(req.packageSize())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(productUseCases.create(product));
    }

    @GetMapping
    @Operation(summary = "Listar produtos")
    public ResponseEntity<List<Product>> findAll() {
        return ResponseEntity.ok(productUseCases.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar produto por ID")
    public ResponseEntity<Product> findById(@PathVariable String id) {
        return ResponseEntity.ok(productUseCases.findById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover peça/insumo do catálogo")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        productUseCases.delete(id);
        return ResponseEntity.noContent().build();
    }
}
