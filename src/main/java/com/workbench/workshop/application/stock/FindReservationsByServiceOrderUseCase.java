package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.stock.port.out.StockReservationRepository;
import com.workbench.workshop.domain.stock.StockReservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Todas as reservas da OS (qualquer status). */
@Service
@RequiredArgsConstructor
public class FindReservationsByServiceOrderUseCase {

    private final StockReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public List<StockReservation> execute(UUID serviceOrderId) {
        return reservationRepository.findByServiceOrderId(serviceOrderId);
    }
}
