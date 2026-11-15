package com.workbench.workshop.adapter.in.web;

import com.workbench.workshop.adapter.in.web.ProductDtos.ProductRequest;
import com.workbench.workshop.adapter.in.web.ProductDtos.ProductResponse;
import com.workbench.workshop.application.product.CreateProductUseCase;
import com.workbench.workshop.application.product.DeleteProductUseCase;
import com.workbench.workshop.application.product.FindProductByIdUseCase;
import com.workbench.workshop.application.product.ListProductsUseCase;
import com.workbench.workshop.application.product.UpdateProductUseCase;
import com.workbench.workshop.domain.product.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product", description = "Cadastro de peças e insumos")
public class ProductController {

    private final CreateProductUseCase createProduct;
    private final ListProductsUseCase listProducts;
    private final FindProductByIdUseCase findProductById;
    private final UpdateProductUseCase updateProduct;
    private final DeleteProductUseCase deleteProduct;

    @PostMapping
    @Operation(summary = "Adicionar peça/insumo ao catálogo")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProductResponse.from(createProduct.execute(req.toProduct())));
    }

    @GetMapping
    @Operation(summary = "Listar produtos")
    public ResponseEntity<List<ProductResponse>> findAll() {
        return ResponseEntity.ok(listProducts.execute().stream().map(ProductResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar produto por ID")
    public ResponseEntity<ProductResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(ProductResponse.from(findProductById.execute(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar peça/insumo", description = "Substitui os dados cadastrais; o SKU continua único.")
    public ResponseEntity<ProductResponse> update(@PathVariable String id, @Valid @RequestBody ProductRequest req) {
        return ResponseEntity.ok(ProductResponse.from(updateProduct.execute(id, req.toProduct())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover peça/insumo do catálogo")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        deleteProduct.execute(id);
        return ResponseEntity.noContent().build();
    }
}
