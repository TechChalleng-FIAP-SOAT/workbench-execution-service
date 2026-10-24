package com.workbench.workshop.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integração end-to-end do Execution Service com MongoDB real (Testcontainers):
 * cria produto + estoque, verifica disponibilidade, reserva, confirma (baixa) e
 * valida que o estoque reflete a regra reserva→baixa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StockIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @BeforeAll
    static void requireDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker indisponível — pulando teste de integração.");
    }

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SECRET = "akmsdkmasfnjsgknakmsdkmasdkmsadfkmdsakmdkmsadsa";

    private static String bearer() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .subject("admin@workbench.dev")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3600000))
                .signWith(key)
                .compact();
        return "Bearer " + token;
    }

    /** Anexa o header Authorization (ADMIN) — as rotas exigem token após a Estratégia 2. */
    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", bearer());
    }

    @Test
    void fullStockFlow_createCheckReserveConfirm() throws Exception {
        // 1. cria produto
        var productJson = mockMvc.perform(auth(post("/api/products"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pastilha de Freio\",\"sku\":\"PF-001\",\"type\":\"PART\",\"unit\":\"UNIT\",\"salePrice\":50.00}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String productId = mapper.readTree(productJson).get("id").asText();

        // 2. cria estoque com 10 unidades
        mockMvc.perform(auth(post("/api/stocks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":10,\"minimumQuantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableQuantity").value(10));

        // 3. disponibilidade suficiente
        mockMvc.perform(auth(post("/api/stocks/availability"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        // 4. reserva 3 → disponível cai para 7, total continua 10
        String osId = UUID.randomUUID().toString();
        mockMvc.perform(auth(post("/api/stocks/reservations"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceOrderId\":\"" + osId + "\",\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":3}]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(auth(get("/api/stocks/product/" + productId)))
                .andExpect(jsonPath("$.availableQuantity").value(7))
                .andExpect(jsonPath("$.quantity").value(10));

        // 5. confirma → baixa: total cai para 7, reservado zera
        mockMvc.perform(auth(patch("/api/stocks/reservations/service-order/" + osId + "/confirm")))
                .andExpect(status().isOk());
        mockMvc.perform(auth(get("/api/stocks/product/" + productId)))
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }

    @Test
    void availabilityReportsShortageWithName() throws Exception {
        var productJson = mockMvc.perform(auth(post("/api/products"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Filtro de Oleo\",\"sku\":\"FO-9\",\"type\":\"PART\",\"unit\":\"UNIT\",\"salePrice\":30.00}"))
                .andReturn().getResponse().getContentAsString();
        String productId = mapper.readTree(productJson).get("id").asText();

        mockMvc.perform(auth(post("/api/stocks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1,\"minimumQuantity\":0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(auth(post("/api/stocks/availability"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":5}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.shortages[0].productName").value("Filtro de Oleo"))
                .andExpect(jsonPath("$.shortages[0].available").value(1));
    }

    @Test
    void reserveInsufficientReturns409() throws Exception {
        var productJson = mockMvc.perform(auth(post("/api/products"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vela\",\"sku\":\"VL-1\",\"type\":\"PART\",\"unit\":\"UNIT\",\"salePrice\":10.00}"))
                .andReturn().getResponse().getContentAsString();
        String productId = mapper.readTree(productJson).get("id").asText();

        mockMvc.perform(auth(post("/api/stocks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1,\"minimumQuantity\":0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(auth(post("/api/stocks/reservations"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceOrderId\":\"" + UUID.randomUUID() + "\",\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":9}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void protectedRouteWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/stocks")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
