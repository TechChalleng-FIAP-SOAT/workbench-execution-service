package com.workbench.workshop.contract;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.application.stock.ConfirmReservationUseCase;
import com.workbench.workshop.application.stock.ReleaseReservationUseCase;
import com.workbench.workshop.application.stock.ReserveStockUseCase;
import com.workbench.workshop.domain.product.Product;
import com.workbench.workshop.domain.product.ProductType;
import com.workbench.workshop.domain.stock.StockReservation;
import com.workbench.workshop.infrastructure.messaging.ExecutionEventPublisher;
import com.workbench.workshop.infrastructure.messaging.MessageEnvelope;
import com.workbench.workshop.infrastructure.messaging.SagaCommandHandler;
import com.workbench.workshop.infrastructure.messaging.SagaMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Contrato da Saga do lado do Execution: nomes de mensagem, o que ele CONSOME
 * (exemplo do contrato é aceito) e o que ele PUBLICA (campos obrigatórios presentes).
 */
class ExecutionSagaContractTest {

    private static final String SERVICE = "execution-service";
    private final SagaContract contract = SagaContract.load();
    private final UUID osId = UUID.randomUUID();

    private ReserveStockUseCase reserveStock;
    private ConfirmReservationUseCase confirmReservation;
    private ReleaseReservationUseCase releaseReservation;
    private ProductRepository productRepository;
    private ExecutionEventPublisher publisher;
    private SagaCommandHandler handler;

    @BeforeEach
    void setUp() {
        reserveStock = mock(ReserveStockUseCase.class);
        confirmReservation = mock(ConfirmReservationUseCase.class);
        releaseReservation = mock(ReleaseReservationUseCase.class);
        productRepository = mock(ProductRepository.class);
        publisher = mock(ExecutionEventPublisher.class);
        handler = new SagaCommandHandler(reserveStock, confirmReservation, releaseReservation, productRepository, publisher);
    }

    @Test
    void messageNamesMatchContract() {
        var names = SagaContract.constantValues(SagaMessages.class);
        assertTrue(contract.typesInvolving(SERVICE).containsAll(names),
                "SagaMessages tem nome fora do contrato: " + names);
        assertTrue(names.containsAll(contract.typesImplementedBy(SERVICE)),
                "SagaMessages não cobre o contrato: " + contract.typesImplementedBy(SERVICE));
    }

    @Test
    void consumesReserveStockAndPublishesStockReserved() {
        StockReservation reservation = StockReservation.activate("s-1", "p-1", osId, new BigDecimal("2"));
        when(reserveStock.execute(eq(osId), any())).thenReturn(List.of(reservation));
        when(productRepository.findById("p-1")).thenReturn(Optional.of(Product.builder()
                .id("p-1").name("Pastilha").sku("PF-1").brand("Marca X")
                .type(ProductType.PART).salePrice(new BigDecimal("50.00")).build()));

        handler.handle(command(SagaMessages.RESERVE_STOCK));

        // O exemplo do contrato chega ao caso de uso com os itens lidos corretamente.
        verify(reserveStock).execute(eq(osId), eq(List.of(
                new ReserveStockUseCase.ReserveItem("p-1", new BigDecimal("2")))));
        MessageEnvelope reply = lastPublished();
        assertEquals(SagaMessages.STOCK_RESERVED, reply.type());
        assertConforms(reply);
    }

    @Test
    void stockReservedConformsEvenWhenProductIsMissingFromCatalog() {
        StockReservation reservation = StockReservation.activate("s-1", "ghost", osId, new BigDecimal("1"));
        when(reserveStock.execute(eq(osId), any())).thenReturn(List.of(reservation));
        when(productRepository.findById("ghost")).thenReturn(Optional.empty());

        handler.handle(command(SagaMessages.RESERVE_STOCK));

        assertConforms(lastPublished());
    }

    @Test
    void reservationFailedConformsToContract() {
        when(reserveStock.execute(eq(osId), any()))
                .thenThrow(new IllegalStateException("Estoque disponível insuficiente"));

        handler.handle(command(SagaMessages.RESERVE_STOCK));

        MessageEnvelope reply = lastPublished();
        assertEquals(SagaMessages.RESERVATION_FAILED, reply.type());
        assertConforms(reply);
    }

    @Test
    void consumesConfirmAndReleaseAndPublishesConformingEvents() {
        when(confirmReservation.execute(osId)).thenReturn(List.of(mock(StockReservation.class)));
        when(releaseReservation.execute(osId)).thenReturn(List.of(mock(StockReservation.class)));

        handler.handle(command(SagaMessages.CONFIRM_RESERVATION));
        MessageEnvelope confirmed = lastPublished();
        assertEquals(SagaMessages.RESERVATION_CONFIRMED, confirmed.type());
        assertConforms(confirmed);

        handler.handle(command(SagaMessages.RELEASE_RESERVATION));
        MessageEnvelope released = lastPublished();
        assertEquals(SagaMessages.RESERVATION_RELEASED, released.type());
        assertConforms(released);
    }

    private MessageEnvelope command(String type) {
        return MessageEnvelope.of(type, osId.toString(), osId.toString(), contract.example(type));
    }

    private MessageEnvelope lastPublished() {
        ArgumentCaptor<MessageEnvelope> captor = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(publisher, atLeastOnce()).publish(captor.capture());
        return captor.getValue();
    }

    private void assertConforms(MessageEnvelope message) {
        assertEquals(List.of(), contract.missingFields(message.type(), message.payload()),
                "campos obrigatórios ausentes em " + message.type());
    }
}
