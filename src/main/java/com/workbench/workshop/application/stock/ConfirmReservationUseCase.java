package com.workbench.workshop.application.stock;

import com.workbench.workshop.domain.stock.StockReservation;
import com.workbench.workshop.domain.stock.StockReservationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Baixa definitiva do estoque reservado (aprovação do orçamento — ComandoConfirmarReserva). */
@Service
@RequiredArgsConstructor
public class ConfirmReservationUseCase {

    private final ReservationTransition transition;

    @Transactional
    public List<StockReservation> execute(UUID serviceOrderId) {
        return transition.apply(serviceOrderId, StockReservationStatus.CONFIRMED);
    }
}
