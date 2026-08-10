package com.aurora.agents.evaluation;

import com.aurora.agents.AgentRefusal;
import java.util.List;

public record AgentEvaluationRun(
    Object output,
    AgentRefusal refusal,
    List<String> toolCalls,
    List<String> evidenceRefs,
    String clientText,
    boolean sufficientSample,
    boolean liftPresent,
    String recommendation,
    Integer minimumExposuresPerVariant) {
  public AgentEvaluationRun {
    toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
    evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
  }
}
