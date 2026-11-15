package com.workbench.workshop.infrastructure.messaging;

/**
 * Nomes dos comandos e eventos da Saga tratados pelo Execution (contrato com o
 * orquestrador do OS Service — mesmo nome no {@code SagaMessages} de lá).
 */
public final class SagaMessages {

    // Comandos recebidos
    public static final String RESERVE_STOCK = "ComandoReservarEstoque";
    public static final String CONFIRM_RESERVATION = "ComandoConfirmarReserva";
    public static final String RELEASE_RESERVATION = "ComandoLiberarReserva";

    // Eventos publicados
    public static final String STOCK_RESERVED = "ReservaConfirmada";
    public static final String RESERVATION_FAILED = "ReservaFalhou";
    public static final String RESERVATION_CONFIRMED = "ReservaBaixada";
    public static final String RESERVATION_RELEASED = "ReservaLiberada";

    private SagaMessages() {}
}
