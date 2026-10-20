package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockMovementRepository;
import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.application.stock.port.out.StockReservationRepository;
import com.workbench.workshop.domain.stock.Stock;
import com.workbench.workshop.domain.stock.StockReservation;
import com.workbench.workshop.domain.stock.StockReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationUseCasesTest {

    private StockRepository stockRepository;
    private StockReservationRepository reservationRepository;
    private StockMovementRepository movementRepository;
    private ReservationUseCases useCases;

    private final UUID osId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        reservationRepository = mock(StockReservationRepository.class);
        movementRepository = mock(StockMovementRepository.class);
        useCases = new ReservationUseCases(stockRepository, reservationRepository, movementRepository);
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Stock stock(String id, String available) {
        Stock s = Stock.builder().id(id).productId("p-" + id)
                .quantity(new BigDecimal(available)).reservedQuantity(BigDecimal.ZERO)
                .availableQuantity(new BigDecimal(available)).build();
        return s;
    }

    @Test
    void reserveCreatesActiveReservation() {
        when(stockRepository.findByProductId("p-s1")).thenReturn(Optional.of(stock("s1", "10")));

        var result = useCases.reserve(osId,
                List.of(new ReservationUseCases.ReserveItem("p-s1", new BigDecimal("4"))));

        assertEquals(1, result.size());
        assertEquals(StockReservationStatus.ACTIVE, result.get(0).getStatus());
        verify(movementRepository).save(any());
    }

    @Test
    void reserveFailsAtomicallyWhenOneItemInsufficient() {
        when(stockRepository.findByProductId("p-s1")).thenReturn(Optional.of(stock("s1", "10")));
        when(stockRepository.findByProductId("p-s2")).thenReturn(Optional.of(stock("s2", "1")));

        assertThrows(IllegalStateException.class, () -> useCases.reserve(osId, List.of(
                new ReservationUseCases.ReserveItem("p-s1", new BigDecimal("2")),
                new ReservationUseCases.ReserveItem("p-s2", new BigDecimal("5")))));

        // Nada reservado: validação ocorre antes de qualquer save de reserva.
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void confirmTransitionsActiveToConfirmed() {
        var reservation = StockReservation.builder().id("r1").stockId("s1").productId("p-s1")
                .serviceOrderId(osId).quantity(new BigDecimal("3")).status(StockReservationStatus.ACTIVE).build();
        when(reservationRepository.findByServiceOrderIdAndStatus(osId, StockReservationStatus.ACTIVE))
                .thenReturn(List.of(reservation));
        Stock s = stock("s1", "10"); s.setReservedQuantity(new BigDecimal("3"));
        when(stockRepository.findById("s1")).thenReturn(Optional.of(s));

        var result = useCases.confirm(osId);

        assertEquals(StockReservationStatus.CONFIRMED, result.get(0).getStatus());
    }

    @Test
    void releaseTransitionsActiveToReleased() {
        var reservation = StockReservation.builder().id("r1").stockId("s1").productId("p-s1")
                .serviceOrderId(osId).quantity(new BigDecimal("3")).status(StockReservationStatus.ACTIVE).build();
        when(reservationRepository.findByServiceOrderIdAndStatus(osId, StockReservationStatus.ACTIVE))
                .thenReturn(List.of(reservation));
        Stock s = stock("s1", "7"); s.setReservedQuantity(new BigDecimal("3"));
        when(stockRepository.findById("s1")).thenReturn(Optional.of(s));

        var result = useCases.release(osId);

        assertEquals(StockReservationStatus.RELEASED, result.get(0).getStatus());
    }

    @Test
    void confirmThrowsWhenNoActiveReservations() {
        when(reservationRepository.findByServiceOrderIdAndStatus(osId, StockReservationStatus.ACTIVE))
                .thenReturn(List.of());
        assertThrows(NoSuchElementException.class, () -> useCases.confirm(osId));
    }
}
