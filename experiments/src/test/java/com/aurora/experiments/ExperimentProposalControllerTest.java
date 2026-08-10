package com.aurora.experiments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aurora.common.martech.ActivationResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExperimentProposalControllerTest {
  private final ExperimentProposalService service = mock(ExperimentProposalService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new ExperimentProposalController(service)).build();

  @Test
  void approvalWithoutIdentityReturnsBadRequest() throws Exception {
    UUID proposalId = UUID.randomUUID();
    when(service.approve(proposalId, "", "review"))
        .thenThrow(new IllegalArgumentException("approver identity is required"));

    mvc.perform(
            post("/api/experiment-proposals/" + proposalId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actor\":\"\",\"reason\":\"review\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("approver identity is required"));
  }

  @Test
  void illegalApprovalTransitionReturnsConflict() throws Exception {
    UUID proposalId = UUID.randomUUID();
    when(service.approve(proposalId, "reviewer", "retry"))
        .thenThrow(new IllegalStateException("Illegal transition from APPROVED"));

    mvc.perform(
            post("/api/experiment-proposals/" + proposalId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actor\":\"reviewer\",\"reason\":\"retry\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Illegal transition from APPROVED"));
  }

  @Test
  void illegalActivationTransitionReturnsConflict() throws Exception {
    UUID proposalId = UUID.randomUUID();
    when(service.activate(proposalId, "operator", "activate"))
        .thenThrow(new IllegalStateException("Illegal transition from PROPOSED"));

    mvc.perform(
            post("/api/experiment-proposals/" + proposalId + "/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actor\":\"operator\",\"reason\":\"activate\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Illegal transition from PROPOSED"));
  }

  @Test
  void illegalRejectionTransitionReturnsConflict() throws Exception {
    UUID proposalId = UUID.randomUUID();
    when(service.reject(proposalId, "reviewer", "reject"))
        .thenThrow(new IllegalStateException("Illegal transition from APPROVED"));

    mvc.perform(
            post("/api/experiment-proposals/" + proposalId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actor\":\"reviewer\",\"reason\":\"reject\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Illegal transition from APPROVED"));
  }

  @Test
  void activatingAlreadyActivatedProposalReturnsConflict() throws Exception {
    UUID proposalId = UUID.randomUUID();
    when(service.activate(proposalId, "operator", "retry"))
        .thenThrow(new IllegalStateException("Illegal transition from ACTIVATED"));

    mvc.perform(
            post("/api/experiment-proposals/" + proposalId + "/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actor\":\"operator\",\"reason\":\"retry\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Illegal transition from ACTIVATED"));
  }

  @Test
  void providerRejectionReturnsBadGatewayWithDestinationAndReason() throws Exception {
    UUID proposalId = UUID.randomUUID();
    ActivationResult result =
        new ActivationResult(
            "booking-destination",
            "key",
            ActivationResult.Status.REJECTED,
            0,
            1,
            "rate limited",
            java.util.Map.of("provider", "test"));
    when(service.activate(proposalId, "operator", "activate"))
        .thenThrow(new MarTechActivationException("campaign", result));

    mvc.perform(
            post("/api/experiment-proposals/" + proposalId + "/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actor\":\"operator\",\"reason\":\"activate\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.destination").value("booking-destination"))
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectedCount").value("1"))
        .andExpect(jsonPath("$.providerReason").value("rate limited"));
  }
}
