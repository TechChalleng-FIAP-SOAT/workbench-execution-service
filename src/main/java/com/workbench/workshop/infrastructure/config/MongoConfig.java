package com.workbench.workshop.infrastructure.config;

import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Força o uuidRepresentation=STANDARD no cliente Mongo.
 *
 * <p>O campo {@code serviceOrderId} das reservas é um {@link java.util.UUID}; sem
 * essa configuração o driver recusa a serialização ("uuidRepresentation has not
 * been specified"). Fazer via customizer (em vez de property) garante o efeito
 * mesmo quando o MongoClientSettings é construído pelo Testcontainers
 * (@ServiceConnection), que ignora a property spring.data.mongodb.uuid-representation.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer uuidRepresentationCustomizer() {
        return builder -> builder.uuidRepresentation(UuidRepresentation.STANDARD);
    }
}
