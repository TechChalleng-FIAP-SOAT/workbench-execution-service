package com.workbench.workshop.infrastructure.messaging;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.application.stock.ReservationUseCases;
import com.workbench.workshop.domain.product.Product;
import com.workbench.workshop.domain.product.ProductType;
import com.workbench.workshop.domain.stock.StockReservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SagaCommandHandlerTest {

    private ReservationUseCases reservationUseCases;
    private ProductRepository productRepository;
    private ExecutionEventPublisher publisher;
    private SagaCommandHandler handler;

    private final UUID osId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reservationUseCases = mock(ReservationUseCases.class);
        productRepository = mock(ProductRepository.class);
        publisher = mock(ExecutionEventPublisher.class);
        handler = new SagaCommandHandler(reservationUseCases, productRepository, publisher);
    }

    private MessageEnvelope cmd(String type, Map<String, Object> payload) {
        return new MessageEnvelope("m1", "saga-1", osId.toString(), type, "2026-01-01T00:00:00Z", payload);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reservarPublishesReservaConfirmadaWithPricedParts() {
        StockReservation reservation = StockReservation.builder()
                .productId("p1").quantity(new BigDecimal("2")).build();
        when(reservationUseCases.reserve(eq(osId), any())).thenReturn(List.of(reservation));
        when(productRepository.findById("p1")).thenReturn(Optional.of(Product.builder()
                .id("p1").name("Pastilha de freio").sku("PF-1").brand("Marca X")
                .type(ProductType.PART).salePrice(new BigDecimal("500.00")).build()));
        var payload = Map.<String, Object>of("items", List.of(Map.of("productId", "p1", "quantity", "2")));

        handler.handle(cmd("ComandoReservarEstoque", payload));

        ArgumentCaptor<MessageEnvelope> captor = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(publisher).publish(captor.capture());
        MessageEnvelope evt = captor.getValue();
        assertEquals("ReservaConfirmada", evt.type());

        List<Map<String, Object>> parts = (List<Map<String, Object>>) evt.payload().get("parts");
        assertEquals(1, parts.size());
        Map<String, Object> part = parts.get(0);
        assertEquals("Marca X", part.get("brand"));
        assertEquals("PART", part.get("itemType"));
        assertEquals(new BigDecimal("500.00"), part.get("unitPrice"));
        assertEquals("p1", part.get("productId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reservarUsesFallbackPartWhenProductMissing() {
        StockReservation reservation = StockReservation.builder()
                .productId("ghost").quantity(new BigDecimal("1")).build();
        when(reservationUseCases.reserve(eq(osId), any())).thenReturn(List.of(reservation));
        when(productRepository.findById("ghost")).thenReturn(Optional.empty());
        var payload = Map.<String, Object>of("items", List.of(Map.of("productId", "ghost", "quantity", "1")));

        handler.handle(cmd("ComandoReservarEstoque", payload));

        ArgumentCaptor<MessageEnvelope> captor = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(publisher).publish(captor.capture());
        List<Map<String, Object>> parts = (List<Map<String, Object>>) captor.getValue().payload().get("parts");
        assertEquals(BigDecimal.ZERO, parts.get(0).get("unitPrice"));
        assertTrue(String.valueOf(parts.get(0).get("name")).contains("ghost"));
    }

    @Test
    void reservarPublishesReservaFalhouOnInsufficientStock() {
        when(reservationUseCases.reserve(eq(osId), any()))
                .thenThrow(new IllegalStateException("Insufficient available stock"));
        var payload = Map.<String, Object>of("items", List.of(Map.of("productId", "p1", "quantity", "99")));

        handler.handle(cmd("ComandoReservarEstoque", payload));

        ArgumentCaptor<MessageEnvelope> captor = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(publisher).publish(captor.capture());
        assertEquals("ReservaFalhou", captor.getValue().type());
    }

    @Test
    void confirmarPublishesReservaBaixada() {
        when(reservationUseCases.confirm(osId)).thenReturn(List.of(mock(StockReservation.class)));
        handler.handle(cmd("ComandoConfirmarReserva", Map.of()));
        ArgumentCaptor<MessageEnvelope> captor = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(publisher).publish(captor.capture());
        assertEquals("ReservaBaixada", captor.getValue().type());
    }

    @Test
    void liberarPublishesReservaLiberada() {
        when(reservationUseCases.release(osId)).thenReturn(List.of(mock(StockReservation.class)));
        handler.handle(cmd("ComandoLiberarReserva", Map.of()));
        ArgumentCaptor<MessageEnvelope> captor = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(publisher).publish(captor.capture());
        assertEquals("ReservaLiberada", captor.getValue().type());
    }

    @Test
    void unknownCommandIsIgnored() {
        handler.handle(cmd("ComandoDesconhecido", Map.of()));
        verify(publisher, never()).publish(any());
    }
}
