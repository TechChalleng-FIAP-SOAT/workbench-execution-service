package com.workbench.workshop.application.product;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.domain.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductUseCasesTest {

    private ProductRepository repository;
    private CreateProductUseCase createProduct;
    private FindProductByIdUseCase findProductById;
    private ListProductsUseCase listProducts;
    private DeleteProductUseCase deleteProduct;
    private UpdateProductUseCase updateProduct;

    @BeforeEach
    void setUp() {
        repository = mock(ProductRepository.class);
        createProduct = new CreateProductUseCase(repository);
        findProductById = new FindProductByIdUseCase(repository);
        listProducts = new ListProductsUseCase(repository);
        deleteProduct = new DeleteProductUseCase(repository);
        updateProduct = new UpdateProductUseCase(repository);
        when(repository.getById(any())).thenCallRealMethod();
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createMarksActive() {
        Product p = Product.builder().name("Pastilha").sku("SKU1").build();
        Product saved = createProduct.execute(p);
        assertTrue(saved.isActive());
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(repository.findById("x")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> findProductById.execute("x"));
    }

    @Test
    void deleteChecksExistenceThenDeletes() {
        when(repository.findById("p1")).thenReturn(Optional.of(Product.builder().id("p1").build()));
        deleteProduct.execute("p1");
        verify(repository).deleteById("p1");
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repository.findById("x")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> deleteProduct.execute("x"));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void findAllDelegates() {
        when(repository.findAll()).thenReturn(List.of());
        assertNotNull(listProducts.execute());
    }

    @Test
    void createRejectsDuplicateSku() {
        when(repository.findBySku("SKU1")).thenReturn(Optional.of(Product.builder().id("p0").sku("SKU1").build()));

        assertThrows(IllegalStateException.class,
                () -> createProduct.execute(Product.builder().name("Outra").sku("SKU1").build()));
        verify(repository, never()).save(any());
    }

    @Test
    void updateReplacesDetailsButKeepsIdentityAndStatus() {
        Product existing = Product.builder().id("p1").name("Pastilha").sku("SKU1").active(true)
                .salePrice(new java.math.BigDecimal("50")).build();
        when(repository.findById("p1")).thenReturn(Optional.of(existing));
        when(repository.findBySku("SKU1")).thenReturn(Optional.of(existing)); // o próprio SKU não conflita

        Product updated = updateProduct.execute("p1", Product.builder().name("Pastilha cerâmica").sku("SKU1")
                .salePrice(new java.math.BigDecimal("65")).build());

        assertEquals("p1", updated.getId());
        assertTrue(updated.isActive());
        assertEquals("Pastilha cerâmica", updated.getName());
        assertEquals(new java.math.BigDecimal("65"), updated.getSalePrice());
    }

    @Test
    void updateRejectsSkuOfAnotherProduct() {
        when(repository.findById("p1")).thenReturn(Optional.of(Product.builder().id("p1").sku("SKU1").build()));
        when(repository.findBySku("SKU2")).thenReturn(Optional.of(Product.builder().id("p2").sku("SKU2").build()));

        assertThrows(IllegalStateException.class,
                () -> updateProduct.execute("p1", Product.builder().name("X").sku("SKU2").build()));
    }

    @Test
    void updateMissingProductIs404() {
        when(repository.findById("x")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class,
                () -> updateProduct.execute("x", Product.builder().sku("S").build()));
    }
}
