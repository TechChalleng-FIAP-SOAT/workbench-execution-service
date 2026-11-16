package com.workbench.workshop.infrastructure.config;

import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonType;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Converte para Decimal128 os valores decimais gravados como texto por versões anteriores
 * (antes de {@code spring.data.mongodb.representation.big-decimal=decimal128}).
 *
 * <p>Roda no start e é idempotente: só toca documentos em que o campo ainda é string, com um
 * update no próprio MongoDB ({@code $toDecimal}), sem ler os documentos para a aplicação. Na 2ª
 * subida em diante não encontra nada para converter.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class DecimalStorageMigration implements ApplicationRunner {

    /** Coleção → campos BigDecimal dos documentos de {@code infrastructure.persistence}. */
    static final Map<String, List<String>> DECIMAL_FIELDS = Map.of(
            "product", List.of("costPrice", "salePrice", "packageSize"),
            "stock", List.of("quantity", "reservedQuantity", "availableQuantity", "minimumQuantity"),
            "stock_movement", List.of("quantity"),
            "stock_reservation", List.of("quantity"));

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        DECIMAL_FIELDS.forEach((collection, fields) -> fields.forEach(field -> convert(collection, field)));
    }

    private void convert(String collection, String field) {
        UpdateResult result = mongoTemplate.getCollection(collection).updateMany(
                Filters.type(field, BsonType.STRING),
                List.of(new Document("$set", new Document(field, new Document("$toDecimal", "$" + field)))));
        if (result.getModifiedCount() > 0) {
            log.info("Decimais migrados de texto para Decimal128: {}.{} ({} documentos)",
                    collection, field, result.getModifiedCount());
        }
    }
}
