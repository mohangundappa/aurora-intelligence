package com.aurora.experiments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExperimentControllerTest {
  private final ExperimentService service = mock(ExperimentService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new ExperimentController(service)).build();

  @Test
  void unknownPerformanceExperimentReturnsNotFound() throws Exception {
    when(service.performance("missing-experiment"))
        .thenThrow(new UnknownExperimentException("missing-experiment", java.util.List.of()));

    mvc.perform(get("/api/experiments/missing-experiment/performance"))
        .andExpect(status().isNotFound());
  }
}
