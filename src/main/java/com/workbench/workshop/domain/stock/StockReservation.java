package com.workbench.workshop.domain.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reserva de estoque de uma OS. Sem setters: o status só muda por {@link #confirm()}
 * (baixa definitiva) ou {@link #release()} (devolução), e ambos exigem a reserva
 * ACTIVE. O builder fica para a reconstrução vinda da persistência.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservation {
    private String id;
    private String stockId;
    private String productId;
    private UUID serviceOrderId;
    private BigDecimal quantity;
    private StockReservationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Reserva nova (ACTIVE) de uma quantidade do estoque para a OS. */
    public static StockReservation activate(String stockId, String productId, UUID serviceOrderId, BigDecimal quantity) {
        return StockReservation.builder()
                .stockId(stockId).productId(productId).serviceOrderId(serviceOrderId)
                .quantity(quantity).status(StockReservationStatus.ACTIVE)
                .build();
    }

    public void confirm() {
        moveTo(StockReservationStatus.CONFIRMED);
    }

    public void release() {
        moveTo(StockReservationStatus.RELEASED);
    }

    private void moveTo(StockReservationStatus target) {
        if (status != StockReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reserva " + id + " está " + status + "; só uma reserva ACTIVE pode virar " + target);
        }
        this.status = target;
    }
}
