package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockMovementRepository;
import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.application.stock.port.out.StockReservationRepository;
import com.workbench.workshop.domain.stock.Stock;
import com.workbench.workshop.domain.stock.StockMovementType;
import com.workbench.workshop.domain.stock.StockReservation;
import com.workbench.workshop.domain.stock.StockReservationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reserva o estoque das peças da OS (comando ComandoReservarEstoque da Saga, ADR-0005).
 *
 * <p>Tudo ou nada: valida todos os itens antes de reservar (transação multi-documento
 * do MongoDB). Idempotente: o comando pode chegar repetido (reenvio por timeout /
 * SQS); se a OS já tem reserva ativa, devolve a existente sem prender estoque de novo.
 */
@Service
@RequiredArgsConstructor
public class ReserveStockUseCase {

    private final StockRepository stockRepository;
    private final StockReservationRepository reservationRepository;
    private final StockMovementRepository movementRepository;

    /** Peça a reservar. */
    public record ReserveItem(String productId, BigDecimal quantity) {}

    private record Validated(Stock stock, ReserveItem item) {}

    @Transactional
    public List<StockReservation> execute(UUID serviceOrderId, List<ReserveItem> items) {
        List<StockReservation> existing = reservationRepository
                .findByServiceOrderIdAndStatus(serviceOrderId, StockReservationStatus.ACTIVE);
        if (!existing.isEmpty()) {
            return existing;
        }
        // Valida tudo antes de aplicar — se faltar qualquer item, nada é reservado.
        List<Validated> validated = new ArrayList<>();
        for (ReserveItem item : items) {
            Stock stock = stockRepository.findByProductId(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Estoque não encontrado para o produto: " + item.productId()));
            if (stock.getAvailableQuantity().compareTo(item.quantity()) < 0) {
                throw new IllegalStateException(
                        "Estoque disponível insuficiente para o produto " + item.productId()
                                + ". Disponível: " + stock.getAvailableQuantity()
                                + ", solicitado: " + item.quantity());
            }
            validated.add(new Validated(stock, item));
        }

        List<StockReservation> reservations = new ArrayList<>();
        for (Validated v : validated) {
            v.stock().reserve(v.item().quantity());
            stockRepository.save(v.stock());
            reservations.add(reservationRepository.save(StockReservation.activate(
                    v.stock().getId(), v.item().productId(), serviceOrderId, v.item().quantity())));
            movementRepository.record(v.stock().getId(), StockMovementType.RESERVATION, v.item().quantity(),
                    "Reserved for OS: " + serviceOrderId);
        }
        return reservations;
    }
}
