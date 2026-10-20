package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockMovementRepository;
import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.domain.stock.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StockUseCasesTest {

    private StockRepository stockRepository;
    private StockMovementRepository movementRepository;
    private StockUseCases useCases;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        movementRepository = mock(StockMovementRepository.class);
        useCases = new StockUseCases(stockRepository, movementRepository);
        when(stockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Stock existing() {
        Stock s = Stock.builder().id("s1").productId("p1")
                .quantity(new BigDecimal("10")).reservedQuantity(BigDecimal.ZERO)
                .availableQuantity(new BigDecimal("10")).minimumQuantity(new BigDecimal("2")).build();
        return s;
    }

    @Test
    void createInitializesAvailable() {
        Stock created = useCases.create("p1", new BigDecimal("8"), new BigDecimal("2"));
        assertEquals(new BigDecimal("8"), created.getAvailableQuantity());
    }

    @Test
    void entryIncrementsAndRecordsMovement() {
        when(stockRepository.findByProductId("p1")).thenReturn(Optional.of(existing()));
        Stock result = useCases.entry("p1", new BigDecimal("5"), "compra");
        assertEquals(new BigDecimal("15"), result.getQuantity());
        verify(movementRepository).save(any());
    }

    @Test
    void exitDecrementsAndRecordsMovement() {
        when(stockRepository.findByProductId("p1")).thenReturn(Optional.of(existing()));
        Stock result = useCases.exit("p1", new BigDecimal("4"), "uso");
        assertEquals(new BigDecimal("6"), result.getQuantity());
        verify(movementRepository).save(any());
    }

    @Test
    void findByProductIdThrowsWhenMissing() {
        when(stockRepository.findByProductId("x")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> useCases.findByProductId("x"));
    }

    @Test
    void updateMinimumPersists() {
        when(stockRepository.findByProductId("p1")).thenReturn(Optional.of(existing()));
        Stock result = useCases.updateMinimum("p1", new BigDecimal("5"));
        assertEquals(new BigDecimal("5"), result.getMinimumQuantity());
    }

    @Test
    void findMovementsDelegates() {
        when(stockRepository.findByProductId("p1")).thenReturn(Optional.of(existing()));
        when(movementRepository.findByStockId("s1")).thenReturn(List.of());
        assertNotNull(useCases.findMovements("p1"));
        verify(movementRepository).findByStockId("s1");
    }
}
