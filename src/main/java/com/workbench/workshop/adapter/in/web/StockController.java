package com.workbench.workshop.adapter.in.web;

import com.workbench.workshop.application.stock.CheckAvailabilityUseCase;
import com.workbench.workshop.application.stock.ReservationUseCases;
import com.workbench.workshop.application.stock.StockUseCases;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "Estoque, disponibilidade e reservas")
public class StockController {

    private final StockUseCases stockUseCases;
    private final ReservationUseCases reservationUseCases;
    private final CheckAvailabilityUseCase checkAvailabilityUseCase;

    @PostMapping
    @Operation(summary = "Criar estoque para um produto")
    public ResponseEntity<StockDtos.StockResponse> create(@Valid @RequestBody StockDtos.CreateStockRequest req) {
        var stock = stockUseCases.create(req.productId(), req.quantity(), req.minimumQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(StockDtos.StockResponse.from(stock));
    }

    @GetMapping
    @Operation(summary = "Listar estoques")
    public ResponseEntity<List<StockDtos.StockResponse>> findAll() {
        return ResponseEntity.ok(stockUseCases.findAll().stream().map(StockDtos.StockResponse::from).toList());
    }

    @GetMapping("/low")
    @Operation(summary = "Listar estoques abaixo do mínimo")
    public ResponseEntity<List<StockDtos.StockResponse>> findLow() {
        return ResponseEntity.ok(stockUseCases.findLowStock().stream().map(StockDtos.StockResponse::from).toList());
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Buscar estoque por produto")
    public ResponseEntity<StockDtos.StockResponse> findByProduct(@PathVariable String productId) {
        return ResponseEntity.ok(StockDtos.StockResponse.from(stockUseCases.findByProductId(productId)));
    }

    @PatchMapping("/product/{productId}/entry")
    @Operation(summary = "Entrada de estoque")
    public ResponseEntity<StockDtos.StockResponse> entry(@PathVariable String productId,
                                                         @Valid @RequestBody StockDtos.MovementRequest req) {
        return ResponseEntity.ok(StockDtos.StockResponse.from(stockUseCases.entry(productId, req.quantity(), req.reason())));
    }

    @PatchMapping("/product/{productId}/exit")
    @Operation(summary = "Saída de estoque")
    public ResponseEntity<StockDtos.StockResponse> exit(@PathVariable String productId,
                                                        @Valid @RequestBody StockDtos.MovementRequest req) {
        return ResponseEntity.ok(StockDtos.StockResponse.from(stockUseCases.exit(productId, req.quantity(), req.reason())));
    }

    @PatchMapping("/product/{productId}/minimum")
    @Operation(summary = "Atualizar quantidade mínima")
    public ResponseEntity<StockDtos.StockResponse> updateMinimum(@PathVariable String productId,
                                                                 @RequestParam BigDecimal minimumQuantity) {
        return ResponseEntity.ok(StockDtos.StockResponse.from(stockUseCases.updateMinimum(productId, minimumQuantity)));
    }

    @PostMapping("/availability")
    @Operation(summary = "Verificar disponibilidade de itens (para montar orçamento)")
    public ResponseEntity<CheckAvailabilityUseCase.AvailabilityResult> availability(
            @Valid @RequestBody StockDtos.AvailabilityRequest req) {
        var items = req.items().stream()
                .map(i -> new CheckAvailabilityUseCase.RequestedItem(i.productId(), i.quantity())).toList();
        return ResponseEntity.ok(checkAvailabilityUseCase.execute(items));
    }

    @PostMapping("/reservations")
    @Operation(summary = "Reservar estoque para uma OS")
    public ResponseEntity<List<StockDtos.ReservationResponse>> reserve(@Valid @RequestBody StockDtos.ReserveRequest req) {
        var items = req.items().stream()
                .map(i -> new ReservationUseCases.ReserveItem(i.productId(), i.quantity())).toList();
        var result = reservationUseCases.reserve(req.serviceOrderId(), items).stream()
                .map(StockDtos.ReservationResponse::from).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PatchMapping("/reservations/service-order/{serviceOrderId}/confirm")
    @Operation(summary = "Confirmar reservas (baixa de estoque)")
    public ResponseEntity<List<StockDtos.ReservationResponse>> confirm(@PathVariable UUID serviceOrderId) {
        return ResponseEntity.ok(reservationUseCases.confirm(serviceOrderId).stream()
                .map(StockDtos.ReservationResponse::from).toList());
    }

    @PatchMapping("/reservations/service-order/{serviceOrderId}/release")
    @Operation(summary = "Liberar reservas (volta ao estoque)")
    public ResponseEntity<List<StockDtos.ReservationResponse>> release(@PathVariable UUID serviceOrderId) {
        return ResponseEntity.ok(reservationUseCases.release(serviceOrderId).stream()
                .map(StockDtos.ReservationResponse::from).toList());
    }

    @GetMapping("/reservations/service-order/{serviceOrderId}")
    @Operation(summary = "Listar reservas de uma OS")
    public ResponseEntity<List<StockDtos.ReservationResponse>> findReservations(@PathVariable UUID serviceOrderId) {
        return ResponseEntity.ok(reservationUseCases.findByServiceOrder(serviceOrderId).stream()
                .map(StockDtos.ReservationResponse::from).toList());
    }
}
