package com.aurora.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aurora.experiments.ExperimentAnalysisService;
import com.aurora.objectives.MarketingObjective;
import com.aurora.objectives.MarketingObjectiveService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExperimentAnalysisControllerTest {
  private final ExperimentAnalysisService analyses = mock(ExperimentAnalysisService.class);
  private final DeterministicAnalyticsRuntime runtime = mock(DeterministicAnalyticsRuntime.class);
  private final MarketingObjectiveService objectives = mock(MarketingObjectiveService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(
              new ExperimentAnalysisController(analyses, runtime, objectives))
          .build();

  @Test
  void missingObjectiveIdReturnsBadRequest() throws Exception {
    mvc.perform(
            post("/api/experiments/experiment/analyses")
                .contentType("application/json")
                .content("{\"objectiveId\":\"\",\"correlationId\":\"corr\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("objectiveId is required"));
  }

  @Test
  void unknownExperimentReturnsNotFound() throws Exception {
    when(objectives.get("objective")).thenReturn(mock(MarketingObjective.class));
    when(runtime.run(any(AnalyticsInput.class), any(String.class)))
        .thenThrow(
            new com.aurora.experiments.UnknownExperimentException(
                "missing", java.util.List.of("known")));

    mvc.perform(
            post("/api/experiments/missing/analyses")
                .contentType("application/json")
                .content("{\"objectiveId\":\"objective\",\"correlationId\":\"corr\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("missing")));
  }
}
