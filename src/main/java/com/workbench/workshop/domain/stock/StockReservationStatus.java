package com.workbench.workshop.domain.stock;

/**
 * Ciclo de vida da reserva de estoque (ver ADR-0005):
 * ACTIVE (reservado/preso) → CONFIRMED (baixa definitiva, na aprovação)
 *                          → RELEASED (devolvido ao disponível, na recusa/falha).
 */
public enum StockReservationStatus {
    ACTIVE, CONFIRMED, RELEASED
}
