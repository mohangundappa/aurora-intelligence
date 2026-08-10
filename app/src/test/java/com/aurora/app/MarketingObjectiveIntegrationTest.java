package com.aurora.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aurora.agents.MarketingInsight;
import com.aurora.agents.MarketingInsightRepository;
import com.aurora.experiments.ExperimentProposal;
import com.aurora.experiments.ExperimentProposalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
  @Autowired MarketingInsightRepository insights;
  @Autowired ExperimentProposalRepository proposals;

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
            .andExpect(jsonPath("$.status").value("REFUSED"))
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

  @Test
  void invalidObjectiveReturnsBadRequestWithValidationMessage() throws Exception {
    mvc.perform(
            post("/api/objectives")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "objectiveId": "invalid-objective",
                      "name": "",
                      "description": "Invalid objective",
                      "businessGoal": "Increase revenue",
                      "targetKpi": "BOOKING_COMPLETED",
                      "targetValue": 25,
                      "targetAudience": "Miami families",
                      "constraints": {},
                      "startDate": "2026-03-31",
                      "endDate": "2026-01-01",
                      "createdBy": "integration-test"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("name is required"));
  }

  @Test
  void approvedProposalActivatesAsDraftAndRecordsHumanAudit() throws Exception {
    mvc.perform(
            post("/api/objectives")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "objectiveId": "proposal-integration-objective",
                      "name": "Increase weekend leisure booking conversion",
                      "description": "Proposal governance integration test",
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

    UUID insightId = UUID.randomUUID();
    insights.save(
        new MarketingInsight(
            insightId,
            "proposal-integration-objective",
            "Observed association",
            "Observed association to test",
            java.util.Map.of("signalName", "weekend-getaway-affinity"),
            List.of("agent-tool-result:1"),
            "proposal-correlation",
            Instant.now()));
    UUID proposalId = UUID.randomUUID();
    proposals.save(
        new ExperimentProposal(
            proposalId,
            "proposal-integration-objective",
            insightId,
            "proposal-integration-experiment",
            "Integration experiment",
            "Integration experiment description",
            "Weekend leisure guests",
            "weekend-getaway-affinity",
            "Test observed association",
            List.of(
                new ExperimentProposal.Variant("treatment", 20),
                new ExperimentProposal.Variant("control", 80)),
            "BOOKING_COMPLETED",
            1,
            BigDecimal.valueOf(0.1),
            "Integration test reasoning",
            List.of("agent-tool-result:1"),
            "proposal-correlation",
            ExperimentProposal.GovernanceState.PROPOSED,
            Instant.now()));

    mvc.perform(
            post("/api/experiment-proposals/" + proposalId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actor\":\"human-reviewer\",\"reason\":\"validated\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.governanceState").value("APPROVED"));
    mvc.perform(
            post("/api/experiment-proposals/" + proposalId + "/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actor\":\"human-reviewer\",\"reason\":\"activate draft\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.governanceState").value("ACTIVATED"));
    mvc.perform(get("/api/experiments/proposal-integration-experiment/performance"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.insufficientSample").value(true));
    mvc.perform(get("/api/experiment-proposals/" + proposalId + "/audit"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].actor").value("human-reviewer"))
        .andExpect(jsonPath("$[1].actor").value("human-reviewer"));
  }
}
