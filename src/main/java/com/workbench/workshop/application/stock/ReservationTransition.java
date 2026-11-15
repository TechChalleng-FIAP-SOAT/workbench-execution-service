package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockMovementRepository;
import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.application.stock.port.out.StockReservationRepository;
import com.workbench.workshop.domain.stock.Stock;
import com.workbench.workshop.domain.stock.StockMovementType;
import com.workbench.workshop.domain.stock.StockReservation;
import com.workbench.workshop.domain.stock.StockReservationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Passa as reservas ACTIVE da OS para CONFIRMED (baixa) ou RELEASED (devolução),
 * ajustando o estoque e registrando o movimento. Compartilhado por
 * {@link ConfirmReservationUseCase} e {@link ReleaseReservationUseCase}; roda na
 * transação de quem chama. Repetido (a transição já foi feita): devolve o resultado anterior.
 * OS sem reserva (só mão de obra, nenhuma peça): devolve lista vazia — não há o que
 * baixar nem liberar, e a Saga precisa da resposta para seguir. Reserva que já seguiu o
 * caminho oposto (baixar o que foi liberado, ou o contrário) é conflito: o estoque não muda.
 */
@Component
@RequiredArgsConstructor
class ReservationTransition {

    private final StockRepository stockRepository;
    private final StockReservationRepository reservationRepository;
    private final StockMovementRepository movementRepository;

    List<StockReservation> apply(UUID serviceOrderId, StockReservationStatus target) {
        List<StockReservation> active = reservationRepository
                .findByServiceOrderIdAndStatus(serviceOrderId, StockReservationStatus.ACTIVE);
        if (active.isEmpty()) {
            List<StockReservation> done = reservationRepository.findByServiceOrderIdAndStatus(serviceOrderId, target);
            StockReservationStatus opposite = target == StockReservationStatus.CONFIRMED
                    ? StockReservationStatus.RELEASED : StockReservationStatus.CONFIRMED;
            if (done.isEmpty() && !reservationRepository.findByServiceOrderIdAndStatus(serviceOrderId, opposite).isEmpty()) {
                throw new IllegalStateException("As reservas da OS " + serviceOrderId + " já estão " + opposite
                        + "; não é possível passá-las para " + target + ".");
            }
            return done;
        }

        List<StockReservation> result = new ArrayList<>();
        for (StockReservation reservation : active) {
            Stock stock = stockRepository.findById(reservation.getStockId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Estoque não encontrado para a reserva: " + reservation.getId()));
            StockMovementType movementType;
            if (target == StockReservationStatus.CONFIRMED) {
                stock.confirmReservation(reservation.getQuantity());
                reservation.confirm();
                movementType = StockMovementType.RESERVATION_CONFIRMED;
            } else {
                stock.releaseReservation(reservation.getQuantity());
                reservation.release();
                movementType = StockMovementType.RESERVATION_RELEASE;
            }
            stockRepository.save(stock);
            result.add(reservationRepository.save(reservation));
            movementRepository.record(stock.getId(), movementType, reservation.getQuantity(),
                    target + " for OS: " + serviceOrderId);
        }
        return result;
    }
}
