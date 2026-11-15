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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationUseCasesTest {

    private StockRepository stockRepository;
    private StockReservationRepository reservationRepository;
    private StockMovementRepository movementRepository;
    private ReserveStockUseCase reserveStock;
    private ConfirmReservationUseCase confirmReservation;
    private ReleaseReservationUseCase releaseReservation;

    private final UUID osId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        reservationRepository = mock(StockReservationRepository.class);
        movementRepository = mock(StockMovementRepository.class);
        reserveStock = new ReserveStockUseCase(stockRepository, reservationRepository, movementRepository);
        var transition = new ReservationTransition(stockRepository, reservationRepository, movementRepository);
        confirmReservation = new ConfirmReservationUseCase(transition);
        releaseReservation = new ReleaseReservationUseCase(transition);
        doCallRealMethod().when(movementRepository).record(any(), any(), any(), any());
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
    void repeatedReserveReturnsExistingWithoutReservingAgain() {
        // Comando repetido (timeout/SQS): a OS já tem reserva ativa.
        StockReservation active = StockReservation.builder().id("r1").stockId("s1").productId("p-s1")
                .serviceOrderId(osId).quantity(new BigDecimal("4")).status(StockReservationStatus.ACTIVE).build();
        when(reservationRepository.findByServiceOrderIdAndStatus(osId, StockReservationStatus.ACTIVE))
                .thenReturn(List.of(active));

        var result = reserveStock.execute(osId,
                List.of(new ReserveStockUseCase.ReserveItem("p-s1", new BigDecimal("4"))));

        assertEquals(List.of(active), result);
        verify(stockRepository, never()).save(any());
    }

    @Test
    void repeatedConfirmReturnsPreviousResult() {
        StockReservation confirmed = StockReservation.builder().id("r1").stockId("s1")
                .serviceOrderId(osId).quantity(new BigDecimal("4")).status(StockReservationStatus.CONFIRMED).build();
        when(reservationRepository.findByServiceOrderIdAndStatus(osId, StockReservationStatus.ACTIVE))
                .thenReturn(List.of());
        when(reservationRepository.findByServiceOrderIdAndStatus(osId, StockReservationStatus.CONFIRMED))
                .thenReturn(List.of(confirmed));

        assertEquals(List.of(confirmed), confirmReservation.execute(osId));
        verify(stockRepository, never()).save(any());
    }

    @Test
    void reserveCreatesActiveReservation() {
        when(stockRepository.findByProductId("p-s1")).thenReturn(Optional.of(stock("s1", "10")));

        var result = reserveStock.execute(osId,
                List.of(new ReserveStockUseCase.ReserveItem("p-s1", new BigDecimal("4"))));

        assertEquals(1, result.size());
        assertEquals(StockReservationStatus.ACTIVE, result.get(0).getStatus());
        verify(movementRepository).save(any());
    }

    @Test
    void reserveFailsAtomicallyWhenOneItemInsufficient() {
        when(stockRepository.findByProductId("p-s1")).thenReturn(Optional.of(stock("s1", "10")));
        when(stockRepository.findByProductId("p-s2")).thenReturn(Optional.of(stock("s2", "1")));

        assertThrows(IllegalStateException.class, () -> reserveStock.execute(osId, List.of(
                new ReserveStockUseCase.ReserveItem("p-s1", new BigDecimal("2")),
                new ReserveStockUseCase.ReserveItem("p-s2", new BigDecimal("5")))));

        // Nada reservado: validação ocorre antes de qualquer save de reserva.
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void confirmTransitionsActiveToConfirmed() {
        var reservation = StockReservation.builder().id("r1").stockId("s1").productId("p-s1")
                .serviceOrderId(osId).quantity(new BigDecimal("3")).status(StockReservationStatus.ACTIVE).build();
        when(reservationRepository.findByServiceOrderIdAndStatus(osId, StockReservationStatus.ACTIVE))
                .thenReturn(List.of(reservation));
        Stock s = stock("s1", "10"); s.reserve(new BigDecimal("3"));
        when(stockRepository.findById("s1")).thenReturn(Optional.of(s));

        var result = confirmReservation.execute(osId);

        assertEquals(StockReservationStatus.CONFIRMED, result.get(0).getStatus());
    }

    @Test
    void releaseTransitionsActiveToReleased() {
        var reservation = StockReservation.builder().id("r1").stockId("s1").productId("p-s1")
                .serviceOrderId(osId).quantity(new BigDecimal("3")).status(StockReservationStatus.ACTIVE).build();
        when(reservationRepository.findByServiceOrderIdAndStatus(osId, StockReservationStatus.ACTIVE))
                .thenReturn(List.of(reservation));
        Stock s = stock("s1", "7"); s.reserve(new BigDecimal("3"));
        when(stockRepository.findById("s1")).thenReturn(Optional.of(s));

        var result = releaseReservation.execute(osId);

        assertEquals(StockReservationStatus.RELEASED, result.get(0).getStatus());
    }

    @Test
    void confirmAfterReleaseIsAConflict() {
        // Ex.: aprovação e recusa simultâneas — a liberação chegou primeiro.
        var released = StockReservation.builder().id("r1").stockId("s1").productId("p-s1")
                .serviceOrderId(osId).quantity(new BigDecimal("3")).status(StockReservationStatus.RELEASED).build();
        when(reservationRepository.findByServiceOrderIdAndStatus(eq(osId), any())).thenReturn(List.of());
        when(reservationRepository.findByServiceOrderIdAndStatus(osId, StockReservationStatus.RELEASED))
                .thenReturn(List.of(released));

        assertThrows(IllegalStateException.class, () -> confirmReservation.execute(osId));
        verify(stockRepository, never()).save(any());
    }

    @Test
    void confirmAndReleaseReturnEmptyWhenOrderHasNoReservation() {
        // OS só com mão de obra: nada foi reservado, então não há o que baixar/liberar.
        when(reservationRepository.findByServiceOrderIdAndStatus(eq(osId), any())).thenReturn(List.of());

        assertTrue(confirmReservation.execute(osId).isEmpty());
        assertTrue(releaseReservation.execute(osId).isEmpty());
        verify(stockRepository, never()).save(any());
    }
}
