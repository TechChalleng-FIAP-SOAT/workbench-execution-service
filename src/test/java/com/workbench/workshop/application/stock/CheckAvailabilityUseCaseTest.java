package com.workbench.workshop.application.stock;

import com.workbench.workshop.application.product.port.out.ProductRepository;
import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.domain.product.Product;
import com.workbench.workshop.domain.stock.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckAvailabilityUseCaseTest {

    private StockRepository stockRepository;
    private ProductRepository productRepository;
    private CheckAvailabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        productRepository = mock(ProductRepository.class);
        useCase = new CheckAvailabilityUseCase(stockRepository, productRepository);
    }

    private Stock stockWithAvailable(String available) {
        return Stock.builder().productId("p1").availableQuantity(new BigDecimal(available)).build();
    }

    private Product product(String name) {
        return Product.builder().id("p1").name(name).build();
    }

    @Test
    void availableWhenEnoughStock() {
        when(productRepository.findById("p1")).thenReturn(Optional.of(product("Pastilha")));
        when(stockRepository.findByProductId("p1")).thenReturn(Optional.of(stockWithAvailable("10")));

        var result = useCase.execute(List.of(new CheckAvailabilityUseCase.RequestedItem("p1", new BigDecimal("3"))));

        assertTrue(result.available());
        assertTrue(result.shortages().isEmpty());
    }

    @Test
    void reportsInsufficientQuantityWithNameAndAvailable() {
        when(productRepository.findById("p1")).thenReturn(Optional.of(product("Pastilha")));
        when(stockRepository.findByProductId("p1")).thenReturn(Optional.of(stockWithAvailable("2")));

        var result = useCase.execute(List.of(new CheckAvailabilityUseCase.RequestedItem("p1", new BigDecimal("5"))));

        assertFalse(result.available());
        var s = result.shortages().get(0);
        assertEquals("Pastilha", s.productName());
        assertEquals(new BigDecimal("2"), s.available());
        assertTrue(s.message().contains("Pastilha"));
    }

    @Test
    void reportsMissingProduct() {
        when(productRepository.findById("x")).thenReturn(Optional.empty());

        var result = useCase.execute(List.of(new CheckAvailabilityUseCase.RequestedItem("x", new BigDecimal("1"))));

        assertFalse(result.available());
        assertEquals("Produto não encontrado", result.shortages().get(0).message());
    }

    @Test
    void reportsNoStockRecord() {
        when(productRepository.findById("p1")).thenReturn(Optional.of(product("Filtro")));
        when(stockRepository.findByProductId("p1")).thenReturn(Optional.empty());

        var result = useCase.execute(List.of(new CheckAvailabilityUseCase.RequestedItem("p1", new BigDecimal("1"))));

        assertFalse(result.available());
        assertTrue(result.shortages().get(0).message().contains("Sem estoque"));
    }
}
