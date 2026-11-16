package com.workbench.workshop.infrastructure.config;

import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Configuração do MongoDB: transações e uuidRepresentation=STANDARD.
 *
 * <p><b>Transações:</b> o Spring Boot não cria um {@link MongoTransactionManager}
 * sozinho; sem ele os {@code @Transactional} dos casos de uso são ignorados. Com ele,
 * reservar/confirmar/liberar é atômico (tudo ou nada) e duas gravações concorrentes no
 * mesmo estoque fazem o MongoDB abortar a segunda (WriteConflict) em vez de perder
 * uma atualização. Exige replica set (Atlas, docker-compose e Testcontainers já são).
 *
 * <p><b>UUID:</b>
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
    public MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Bean
    public MongoClientSettingsBuilderCustomizer uuidRepresentationCustomizer() {
        return builder -> builder.uuidRepresentation(UuidRepresentation.STANDARD);
    }
}
