package com.workbench.workshop.adapter.out.persistence;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.domain.product.Product;
import com.workbench.workshop.infrastructure.persistence.ProductDocument;
import com.workbench.workshop.infrastructure.persistence.ProductMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepository {

    private final ProductMongoRepository repository;

    @Override
    public Product save(Product p) {
        LocalDateTime now = LocalDateTime.now();
        ProductDocument doc = ProductDocument.builder()
                .id(p.getId())
                .name(p.getName())
                .sku(p.getSku())
                .type(p.getType())
                .unit(p.getUnit())
                .category(p.getCategory())
                .brand(p.getBrand())
                .costPrice(p.getCostPrice())
                .salePrice(p.getSalePrice())
                .active(p.isActive())
                .manufacturerCode(p.getManufacturerCode())
                .warrantyMonths(p.getWarrantyMonths())
                .fractionalAllowed(p.getFractionalAllowed())
                .packageSize(p.getPackageSize())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt() : now)
                .updatedAt(now)
                .build();
        return toDomain(repository.save(doc));
    }

    @Override
    public Optional<Product> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        return repository.findBySku(sku).map(this::toDomain);
    }

    @Override
    public List<Product> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    private Product toDomain(ProductDocument d) {
        return Product.builder()
                .id(d.getId()).name(d.getName()).sku(d.getSku()).type(d.getType()).unit(d.getUnit())
                .category(d.getCategory()).brand(d.getBrand()).costPrice(d.getCostPrice()).salePrice(d.getSalePrice())
                .active(d.isActive()).manufacturerCode(d.getManufacturerCode()).warrantyMonths(d.getWarrantyMonths())
                .fractionalAllowed(d.getFractionalAllowed()).packageSize(d.getPackageSize())
                .createdAt(d.getCreatedAt()).updatedAt(d.getUpdatedAt())
                .build();
    }
}
