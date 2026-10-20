package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockMovementRepository;
import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.application.stock.port.out.StockReservationRepository;
import com.workbench.workshop.domain.stock.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Casos de uso da reserva de estoque — o coração da Saga (ADR-0005).
 *
 * <p>Os métodos são {@code @Transactional}: no MongoDB isso abre uma sessão
 * transacional multi-documento, garantindo que o ajuste do estoque, o registro da
 * reserva e o movimento sejam atômicos (tudo ou nada), como no monólito relacional.
 */
@Service
@RequiredArgsConstructor
public class ReservationUseCases {

    private final StockRepository stockRepository;
    private final StockReservationRepository reservationRepository;
    private final StockMovementRepository movementRepository;

    @Transactional
    public List<StockReservation> reserve(UUID serviceOrderId, List<ReserveItem> items) {
        // Valida tudo antes de aplicar — se faltar qualquer item, nada é reservado.
        List<Context> contexts = new ArrayList<>();
        for (ReserveItem item : items) {
            Stock stock = stockRepository.findByProductId(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Stock not found for product: " + item.productId()));
            if (stock.getAvailableQuantity().compareTo(item.quantity()) < 0) {
                throw new IllegalStateException(
                        "Insufficient available stock for product " + item.productId()
                                + ". Available: " + stock.getAvailableQuantity()
                                + ", Requested: " + item.quantity());
            }
            contexts.add(new Context(stock, item.productId(), item.quantity()));
        }

        List<StockReservation> reservations = new ArrayList<>();
        for (Context ctx : contexts) {
            ctx.stock().reserve(ctx.quantity());
            stockRepository.save(ctx.stock());

            reservations.add(reservationRepository.save(StockReservation.builder()
                    .stockId(ctx.stock().getId())
                    .productId(ctx.productId())
                    .serviceOrderId(serviceOrderId)
                    .quantity(ctx.quantity())
                    .status(StockReservationStatus.ACTIVE)
                    .build()));

            recordMovement(ctx.stock().getId(), StockMovementType.RESERVATION, ctx.quantity(),
                    "Reserved for OS: " + serviceOrderId);
        }
        return reservations;
    }

    @Transactional
    public List<StockReservation> confirm(UUID serviceOrderId) {
        return transition(serviceOrderId, StockReservationStatus.CONFIRMED);
    }

    @Transactional
    public List<StockReservation> release(UUID serviceOrderId) {
        return transition(serviceOrderId, StockReservationStatus.RELEASED);
    }

    public List<StockReservation> findByServiceOrder(UUID serviceOrderId) {
        return reservationRepository.findByServiceOrderId(serviceOrderId);
    }

    private List<StockReservation> transition(UUID serviceOrderId, StockReservationStatus target) {
        List<StockReservation> active = reservationRepository
                .findByServiceOrderIdAndStatus(serviceOrderId, StockReservationStatus.ACTIVE);
        if (active.isEmpty()) {
            throw new NoSuchElementException("No active reservations found for OS: " + serviceOrderId);
        }

        List<StockReservation> result = new ArrayList<>();
        for (StockReservation reservation : active) {
            Stock stock = stockRepository.findById(reservation.getStockId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Stock not found for reservation: " + reservation.getId()));

            StockMovementType movementType;
            if (target == StockReservationStatus.CONFIRMED) {
                stock.confirmReservation(reservation.getQuantity());
                movementType = StockMovementType.RESERVATION_CONFIRMED;
            } else {
                stock.releaseReservation(reservation.getQuantity());
                movementType = StockMovementType.RESERVATION_RELEASE;
            }
            stockRepository.save(stock);

            reservation.setStatus(target);
            result.add(reservationRepository.save(reservation));
            recordMovement(stock.getId(), movementType, reservation.getQuantity(),
                    target + " for OS: " + serviceOrderId);
        }
        return result;
    }

    private void recordMovement(String stockId, StockMovementType type, BigDecimal quantity, String reason) {
        movementRepository.save(StockMovement.builder()
                .stockId(stockId).type(type).quantity(quantity).reason(reason).build());
    }

    public record ReserveItem(String productId, BigDecimal quantity) {}
    private record Context(Stock stock, String productId, BigDecimal quantity) {}
}
