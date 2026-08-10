package com.aurora.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class MarketingObjectiveIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("aurora")
          .withUsername("aurora")
          .withPassword("aurora");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired MockMvc mvc;

  @Test
  void migrationAndObjectiveApiSupportCreateAndLifecycleTransition() throws Exception {
    mvc.perform(
            post("/api/objectives")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "objectiveId": "integration-objective",
                      "name": "Increase direct bookings",
                      "description": "Grow direct bookings from family audiences",
                      "businessGoal": "Increase revenue",
                      "targetKpi": "BOOKING_COMPLETED",
                      "targetValue": 25,
                      "targetAudience": "Miami families",
                      "constraints": {"maxDiscount": 15},
                      "startDate": "2026-01-01",
                      "endDate": "2026-03-31",
                      "createdBy": "integration-test"
                    }
                    """))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/objectives/integration-objective/status/ACTIVE")
                .param("actor", "integration-test"))
        .andExpect(status().isOk());
  }

  @Test
  void insightExecutionApiPersistsDeterministicExecutionAndToolEvidenceLink() throws Exception {
    mvc.perform(
            post("/api/objectives")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "objectiveId": "insight-integration-objective",
                      "name": "Increase weekend leisure booking conversion",
                      "description": "Grounded insight integration test",
                      "businessGoal": "Increase bookings",
                      "targetKpi": "BOOKING_COMPLETED",
                      "targetValue": 10,
                      "targetAudience": "Weekend leisure guests",
                      "constraints": {},
                      "startDate": "2026-01-01",
                      "endDate": "2026-03-31",
                      "createdBy": "integration-test"
                    }
                    """))
        .andExpect(status().isOk());

    String executionId =
        mvc.perform(post("/api/objectives/insight-integration-objective/insights"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.model").value("deterministic"))
            .andExpect(jsonPath("$.inputTokenCount").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"executionId\":\"([^\"]+)\".*", "$1");

    mvc.perform(get("/api/agent-executions/" + executionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executionId").value(executionId))
        .andExpect(jsonPath("$.toolCalls").isArray());
  }
}
