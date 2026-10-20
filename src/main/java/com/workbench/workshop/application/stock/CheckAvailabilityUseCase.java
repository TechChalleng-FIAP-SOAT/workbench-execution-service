package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.domain.product.Product;
import com.workbench.workshop.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Verifica a disponibilidade de estoque para um conjunto de itens (ADR-0005).
 *
 * <p>Usado pela montagem do orçamento (via REST). Quando falta item, retorna
 * exatamente o que o negócio pediu: o <b>nome da peça</b> e a <b>quantidade
 * disponível</b> (ou que o produto não existe / sem estoque).
 */
@Service
@RequiredArgsConstructor
public class CheckAvailabilityUseCase {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;

    public AvailabilityResult execute(List<RequestedItem> items) {
        List<Shortage> shortages = new ArrayList<>();

        for (RequestedItem item : items) {
            Optional<Product> product = productRepository.findById(item.productId());
            String name = product.map(Product::getName).orElse(null);

            if (product.isEmpty()) {
                shortages.add(new Shortage(item.productId(), null, item.quantity(), BigDecimal.ZERO,
                        "Produto não encontrado"));
                continue;
            }

            Optional<Stock> stock = stockRepository.findByProductId(item.productId());
            BigDecimal available = stock.map(Stock::getAvailableQuantity).orElse(BigDecimal.ZERO);

            if (stock.isEmpty()) {
                shortages.add(new Shortage(item.productId(), name, item.quantity(), BigDecimal.ZERO,
                        "Sem estoque para a peça " + name));
            } else if (available.compareTo(item.quantity()) < 0) {
                shortages.add(new Shortage(item.productId(), name, item.quantity(), available,
                        "Quantidade insuficiente da peça " + name
                                + " (solicitado: " + item.quantity() + ", disponível: " + available + ")"));
            }
        }

        return new AvailabilityResult(shortages.isEmpty(), shortages);
    }

    public record RequestedItem(String productId, BigDecimal quantity) {}

    public record Shortage(String productId, String productName, BigDecimal requested,
                           BigDecimal available, String message) {}

    public record AvailabilityResult(boolean available, List<Shortage> shortages) {}
}
