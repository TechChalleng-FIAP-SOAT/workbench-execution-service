package com.workbench.workshop.domain.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agregado de estoque (rich domain, portado fielmente do monólito).
 *
 * <p>Regra reserva→baixa→retorno (ADR-0005):
 * <ul>
 *   <li>{@link #reserve} — prende quantidade (incrementa {@code reserved}); o estoque
 *       continua existindo (não baixa), só reduz o disponível.</li>
 *   <li>{@link #confirmReservation} — baixa definitiva: reduz {@code reserved} E
 *       {@code quantity} (saída real, na aprovação da OS).</li>
 *   <li>{@link #releaseReservation} — devolve ao disponível: reduz {@code reserved}
 *       (na recusa/falha).</li>
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {

    private String id;
    private String productId;
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal reservedQuantity = BigDecimal.ZERO;
    private BigDecimal availableQuantity = BigDecimal.ZERO;
    private BigDecimal minimumQuantity = BigDecimal.ZERO;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void recalculateAvailableQuantity() {
        this.availableQuantity = this.quantity.subtract(this.reservedQuantity);
    }

    /** Altera o mínimo (alerta de estoque baixo); não pode ser negativo. */
    public void changeMinimum(BigDecimal minimum) {
        if (minimum == null || minimum.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Quantidade mínima deve ser zero ou positiva");
        }
        this.minimumQuantity = minimum;
    }

    public boolean isLowStock() {
        return availableQuantity.compareTo(minimumQuantity) <= 0;
    }

    public void addQuantity(BigDecimal amount) {
        requirePositive(amount, "Quantidade de entrada deve ser positiva");
        this.quantity = this.quantity.add(amount);
        recalculateAvailableQuantity();
    }

    public void removeQuantity(BigDecimal amount) {
        requirePositive(amount, "Quantidade de saída deve ser positiva");
        if (this.availableQuantity.compareTo(amount) < 0) {
            throw new IllegalStateException("Estoque disponível insuficiente. Disponível: " + this.availableQuantity);
        }
        this.quantity = this.quantity.subtract(amount);
        recalculateAvailableQuantity();
    }

    public void reserve(BigDecimal amount) {
        requirePositive(amount, "Quantidade da reserva deve ser positiva");
        if (this.availableQuantity.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Estoque disponível insuficiente para reservar. Disponível: " + this.availableQuantity);
        }
        this.reservedQuantity = this.reservedQuantity.add(amount);
        recalculateAvailableQuantity();
    }

    public void releaseReservation(BigDecimal amount) {
        requirePositive(amount, "Quantidade a liberar deve ser positiva");
        if (this.reservedQuantity.compareTo(amount) < 0) {
            throw new IllegalStateException("Não é possível liberar mais do que o reservado. Reservado: " + this.reservedQuantity);
        }
        this.reservedQuantity = this.reservedQuantity.subtract(amount);
        recalculateAvailableQuantity();
    }

    public void confirmReservation(BigDecimal amount) {
        requirePositive(amount, "Quantidade a confirmar deve ser positiva");
        if (this.reservedQuantity.compareTo(amount) < 0) {
            throw new IllegalStateException("Não é possível confirmar mais do que o reservado. Reservado: " + this.reservedQuantity);
        }
        this.reservedQuantity = this.reservedQuantity.subtract(amount);
        this.quantity = this.quantity.subtract(amount);
        recalculateAvailableQuantity();
    }

    private static void requirePositive(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
