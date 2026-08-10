package com.aurora.agents.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class AgentEvaluationDataset {
  private static final String RESOURCE = "/evaluation/agent-evaluation-dataset.json";

  private AgentEvaluationDataset() {}

  public static List<AgentEvaluationScenario> load(ObjectMapper mapper) {
    try (InputStream input = AgentEvaluationDataset.class.getResourceAsStream(RESOURCE)) {
      if (input == null) throw new IllegalStateException("Missing evaluation dataset " + RESOURCE);
      return mapper.readValue(input, new TypeReference<>() {});
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load evaluation dataset " + RESOURCE, exception);
    }
  }
}
