package com.workbench.workshop.domain.stock;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class StockTest {

    private Stock stock(String qty, String reserved) {
        Stock s = Stock.builder()
                .productId("p1")
                .quantity(new BigDecimal(qty))
                .reservedQuantity(new BigDecimal(reserved))
                .minimumQuantity(new BigDecimal("2"))
                .build();
        s.recalculateAvailableQuantity();
        return s;
    }

    @Test
    void reserveLocksAvailableWithoutLoweringTotal() {
        Stock s = stock("10", "0");
        s.reserve(new BigDecimal("3"));
        assertEquals(new BigDecimal("10"), s.getQuantity());       // total intacto
        assertEquals(new BigDecimal("3"), s.getReservedQuantity()); // preso
        assertEquals(new BigDecimal("7"), s.getAvailableQuantity());
    }

    @Test
    void confirmReservationBaixaReservedAndTotal() {
        Stock s = stock("10", "3");
        s.confirmReservation(new BigDecimal("3"));
        assertEquals(new BigDecimal("7"), s.getQuantity());        // baixou do total
        assertEquals(new BigDecimal("0"), s.getReservedQuantity());
        assertEquals(new BigDecimal("7"), s.getAvailableQuantity());
    }

    @Test
    void releaseReservationReturnsToAvailable() {
        Stock s = stock("10", "3");
        s.releaseReservation(new BigDecimal("3"));
        assertEquals(new BigDecimal("10"), s.getQuantity());       // total intacto
        assertEquals(new BigDecimal("0"), s.getReservedQuantity());
        assertEquals(new BigDecimal("10"), s.getAvailableQuantity());
    }

    @Test
    void reserveRejectsWhenInsufficient() {
        Stock s = stock("5", "4"); // disponível = 1
        assertThrows(IllegalStateException.class, () -> s.reserve(new BigDecimal("2")));
    }

    @Test
    void removeRejectsWhenInsufficientAvailable() {
        Stock s = stock("5", "4"); // disponível = 1
        assertThrows(IllegalStateException.class, () -> s.removeQuantity(new BigDecimal("2")));
    }

    @Test
    void addIncreasesTotalAndAvailable() {
        Stock s = stock("5", "0");
        s.addQuantity(new BigDecimal("5"));
        assertEquals(new BigDecimal("10"), s.getQuantity());
        assertEquals(new BigDecimal("10"), s.getAvailableQuantity());
    }

    @Test
    void rejectsNonPositiveAmounts() {
        Stock s = stock("5", "0");
        assertThrows(IllegalArgumentException.class, () -> s.reserve(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> s.addQuantity(new BigDecimal("-1")));
    }

    @Test
    void confirmAndReleaseRejectMoreThanReserved() {
        Stock s = stock("10", "2");
        assertThrows(IllegalStateException.class, () -> s.confirmReservation(new BigDecimal("3")));
        assertThrows(IllegalStateException.class, () -> s.releaseReservation(new BigDecimal("3")));
    }

    @Test
    void isLowStockWhenAtOrBelowMinimum() {
        assertTrue(stock("2", "0").isLowStock());   // 2 <= 2
        assertFalse(stock("10", "0").isLowStock());
    }
}
