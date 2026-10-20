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
    private ProductUseCases useCases;

    @BeforeEach
    void setUp() {
        repository = mock(ProductRepository.class);
        useCases = new ProductUseCases(repository);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createMarksActive() {
        Product p = Product.builder().name("Pastilha").sku("SKU1").build();
        Product saved = useCases.create(p);
        assertTrue(saved.isActive());
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(repository.findById("x")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> useCases.findById("x"));
    }

    @Test
    void deleteChecksExistenceThenDeletes() {
        when(repository.findById("p1")).thenReturn(Optional.of(Product.builder().id("p1").build()));
        useCases.delete("p1");
        verify(repository).deleteById("p1");
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repository.findById("x")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> useCases.delete("x"));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void findAllDelegates() {
        when(repository.findAll()).thenReturn(List.of());
        assertNotNull(useCases.findAll());
    }
}
