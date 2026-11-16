package com.workbench.workshop.integration;

import com.workbench.workshop.application.stock.port.out.StockRepository;
import com.workbench.workshop.domain.stock.Stock;
import com.workbench.workshop.infrastructure.config.DecimalStorageMigration;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Decimais gravados como Decimal128 (número), não como texto: a comparação no banco passa a ser
 * numérica. Também cobre a migração dos documentos antigos, gravados como string.
 */
@SpringBootTest
@Testcontainers
class DecimalStorageIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @BeforeAll
    static void requireDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker indisponível — pulando teste de integração.");
    }

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private StockRepository stockRepository;
    @Autowired private DecimalStorageMigration migration;

    @Test
    void newStockIsStoredAsDecimal128() {
        Stock saved = stockRepository.save(Stock.builder()
                .productId("p-" + UUID.randomUUID())
                .quantity(new BigDecimal("12.5"))
                .reservedQuantity(BigDecimal.ZERO)
                .availableQuantity(new BigDecimal("12.5"))
                .minimumQuantity(new BigDecimal("2"))
                .build());

        Document raw = mongoTemplate.getCollection("stock").find(new Document("_id", new org.bson.types.ObjectId(saved.getId()))).first();
        assertInstanceOf(Decimal128.class, raw.get("quantity"));
        assertEquals(new BigDecimal("12.5"), ((Decimal128) raw.get("quantity")).bigDecimalValue());
    }

    @Test
    void textDecimalsFromOlderVersionsAreMigratedAndCompareAsNumbers() {
        // Como texto, "10" <= "9" é verdadeiro (comparação letra a letra): o estoque com 10
        // disponíveis e mínimo 9 aparecia como "baixo". Depois da migração, só o de 9 < 10 aparece.
        String healthy = "p-ok-" + UUID.randomUUID();
        String low = "p-low-" + UUID.randomUUID();
        mongoTemplate.getCollection("stock").insertMany(List.of(
                legacyStock(healthy, "10", "9"),
                legacyStock(low, "9", "10")));

        migration.run(null);

        Document raw = mongoTemplate.getCollection("stock").find(new Document("productId", healthy)).first();
        assertInstanceOf(Decimal128.class, raw.get("availableQuantity"));
        List<String> lowStock = stockRepository.findLowStock().stream().map(Stock::getProductId).toList();
        assertEquals(true, lowStock.contains(low));
        assertEquals(false, lowStock.contains(healthy));
    }

    private static Document legacyStock(String productId, String available, String minimum) {
        return new Document("productId", productId)
                .append("quantity", available)
                .append("reservedQuantity", "0")
                .append("availableQuantity", available)
                .append("minimumQuantity", minimum);
    }
}
