package com.workbench.workshop.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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

    @Test
    void fullStockFlow_createCheckReserveConfirm() throws Exception {
        // 1. cria produto
        var productJson = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pastilha de Freio\",\"sku\":\"PF-001\",\"type\":\"PART\",\"unit\":\"UNIT\",\"salePrice\":50.00}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String productId = mapper.readTree(productJson).get("id").asText();

        // 2. cria estoque com 10 unidades
        mockMvc.perform(post("/api/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":10,\"minimumQuantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableQuantity").value(10));

        // 3. disponibilidade suficiente
        mockMvc.perform(post("/api/stocks/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        // 4. reserva 3 → disponível cai para 7, total continua 10
        String osId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/stocks/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceOrderId\":\"" + osId + "\",\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":3}]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/stocks/product/" + productId))
                .andExpect(jsonPath("$.availableQuantity").value(7))
                .andExpect(jsonPath("$.quantity").value(10));

        // 5. confirma → baixa: total cai para 7, reservado zera
        mockMvc.perform(patch("/api/stocks/reservations/service-order/" + osId + "/confirm"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/stocks/product/" + productId))
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }

    @Test
    void availabilityReportsShortageWithName() throws Exception {
        var productJson = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Filtro de Oleo\",\"sku\":\"FO-9\",\"type\":\"PART\",\"unit\":\"UNIT\",\"salePrice\":30.00}"))
                .andReturn().getResponse().getContentAsString();
        String productId = mapper.readTree(productJson).get("id").asText();

        mockMvc.perform(post("/api/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1,\"minimumQuantity\":0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/stocks/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":5}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.shortages[0].productName").value("Filtro de Oleo"))
                .andExpect(jsonPath("$.shortages[0].available").value(1));
    }

    @Test
    void reserveInsufficientReturns409() throws Exception {
        var productJson = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vela\",\"sku\":\"VL-1\",\"type\":\"PART\",\"unit\":\"UNIT\",\"salePrice\":10.00}"))
                .andReturn().getResponse().getContentAsString();
        String productId = mapper.readTree(productJson).get("id").asText();

        mockMvc.perform(post("/api/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1,\"minimumQuantity\":0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/stocks/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceOrderId\":\"" + UUID.randomUUID() + "\",\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":9}]}"))
                .andExpect(status().isConflict());
    }
}
