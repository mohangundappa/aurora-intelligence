package com.aurora.agents.evaluation;

import java.util.List;
import java.util.Map;

public record AgentEvaluationScenario(
    String id,
    String agent,
    String fixture,
    String expectedRefusalCode,
    List<String> obligations,
    Map<String, Object> fixtureData) {
  public AgentEvaluationScenario {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("scenario id is required");
    if (agent == null || agent.isBlank()) throw new IllegalArgumentException("agent is required");
    if (fixture == null || fixture.isBlank()) {
      throw new IllegalArgumentException("fixture is required");
    }
    obligations = List.copyOf(obligations == null ? List.of() : obligations);
    fixtureData = Map.copyOf(fixtureData == null ? Map.of() : fixtureData);
  }
}
