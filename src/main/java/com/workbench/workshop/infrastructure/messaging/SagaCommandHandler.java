package com.workbench.workshop.infrastructure.messaging;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.application.stock.ReservationUseCases;
import com.workbench.workshop.domain.product.Product;
import com.workbench.workshop.domain.stock.StockReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Traduz os comandos da Saga (recebidos por SQS) em chamadas aos casos de uso de
 * reserva e publica os eventos de resposta no tópico execution-events.
 *
 * <p>Comandos tratados (ADR-0005 / Contratos de Mensagem):
 * <ul>
 *   <li>{@code ComandoReservarEstoque} → reserva ACTIVE → {@code ReservaConfirmada}
 *       ou {@code ReservaFalhou} (com motivo).</li>
 *   <li>{@code ComandoConfirmarReserva} → baixa de estoque → {@code ReservaBaixada}.</li>
 *   <li>{@code ComandoLiberarReserva} → devolve ao estoque → {@code ReservaLiberada}.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final ReservationUseCases reservationUseCases;
    private final ProductRepository productRepository;
    private final ExecutionEventPublisher publisher;

    @SuppressWarnings("unchecked")
    public void handle(MessageEnvelope cmd) {
        UUID serviceOrderId = UUID.fromString(cmd.serviceOrderId());
        log.info("Comando recebido: type={}, serviceOrderId={}", cmd.type(), serviceOrderId);
        switch (cmd.type()) {
            case "ComandoReservarEstoque" -> reservar(cmd, serviceOrderId);
            case "ComandoConfirmarReserva" -> {
                List<StockReservation> confirmed = reservationUseCases.confirm(serviceOrderId);
                log.info("Reserva baixada (estoque confirmado): reservations={}, serviceOrderId={}",
                        confirmed.size(), serviceOrderId);
                publisher.publish(MessageEnvelope.of("ReservaBaixada", cmd.sagaId(), cmd.serviceOrderId(),
                        Map.of("reservations", confirmed.size())));
            }
            case "ComandoLiberarReserva" -> {
                List<StockReservation> released = reservationUseCases.release(serviceOrderId);
                log.info("Reserva liberada (compensação de estoque): reservations={}, serviceOrderId={}",
                        released.size(), serviceOrderId);
                publisher.publish(MessageEnvelope.of("ReservaLiberada", cmd.sagaId(), cmd.serviceOrderId(),
                        Map.of("reservations", released.size())));
            }
            default -> log.warn("Comando desconhecido ignorado: type={}", cmd.type());
        }
    }

    /**
     * Monta as peças precificadas para o {@code ReservaConfirmada}: para cada reserva,
     * busca o produto (dono do catálogo) e congela name/sku/brand/itemType/unitPrice.
     * Esse snapshot é o que o Billing usará no orçamento — sem acessar o Execution.
     */
    private List<Map<String, Object>> pricedParts(List<StockReservation> reservations) {
        List<Map<String, Object>> parts = new ArrayList<>();
        for (StockReservation r : reservations) {
            Product p = productRepository.findById(r.getProductId()).orElse(null);
            Map<String, Object> part = new java.util.HashMap<>();
            part.put("productId", r.getProductId());
            part.put("quantity", r.getQuantity());
            if (p != null) {
                part.put("name", p.getName());
                part.put("sku", p.getSku());
                part.put("brand", p.getBrand());
                part.put("itemType", p.getType() == null ? "PART" : p.getType().name());
                part.put("unitPrice", p.getSalePrice());
            } else {
                // Produto não encontrado no catálogo: envia o mínimo; o orçamento
                // ainda é gerável, mas sem preço (fica a cargo de revisão manual).
                part.put("name", "Produto " + r.getProductId());
                part.put("itemType", "PART");
                part.put("unitPrice", BigDecimal.ZERO);
            }
            parts.add(part);
        }
        return parts;
    }

    @SuppressWarnings("unchecked")
    private void reservar(MessageEnvelope cmd, UUID serviceOrderId) {
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) cmd.payload().getOrDefault("items", List.of());
        List<ReservationUseCases.ReserveItem> items = rawItems.stream()
                .map(i -> new ReservationUseCases.ReserveItem(
                        String.valueOf(i.get("productId")),
                        new BigDecimal(String.valueOf(i.get("quantity")))))
                .toList();
        try {
            var reservations = reservationUseCases.reserve(serviceOrderId, items);
            log.info("Reserva confirmada (estoque reservado): reservations={}, serviceOrderId={}",
                    reservations.size(), serviceOrderId);
            // Enriquece a resposta com as peças precificadas (name/sku/brand/unitPrice):
            // é o Execution, dono do catálogo, quem resolve marca e preço do productId.
            // O orquestrador repassa essas parts ao Billing no ComandoGerarOrcamento.
            publisher.publish(MessageEnvelope.of("ReservaConfirmada", cmd.sagaId(), cmd.serviceOrderId(),
                    Map.of("reservations", reservations.size(),
                            "parts", pricedParts(reservations))));
        } catch (RuntimeException e) {
            // Falha de reserva (estoque insuficiente/inexistente) é um resultado de
            // negócio da Saga: responde ReservaFalhou (não relança — a mensagem é
            // consumida; a compensação cabe ao orquestrador).
            log.warn("Reserva falhou para OS {}: {}", serviceOrderId, e.getMessage());
            publisher.publish(MessageEnvelope.of("ReservaFalhou", cmd.sagaId(), cmd.serviceOrderId(),
                    Map.of("reason", e.getMessage())));
        }
    }
}
