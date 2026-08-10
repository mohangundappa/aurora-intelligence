package com.aurora.experiments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
