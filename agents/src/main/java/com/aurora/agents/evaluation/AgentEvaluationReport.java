package com.aurora.agents.evaluation;

import java.util.List;

public record AgentEvaluationReport(List<ScenarioResult> scenarios) {
  public AgentEvaluationReport {
    scenarios = List.copyOf(scenarios);
  }

  public long passed() {
    return scenarios.stream().filter(ScenarioResult::passed).count();
  }

  public long failed() {
    return scenarios.size() - passed();
  }

  public String summary() {
    StringBuilder summary =
        new StringBuilder()
            .append("Agent evaluation: ")
            .append(passed())
            .append(" passed, ")
            .append(failed())
            .append(" failed");
    scenarios.stream()
        .filter(result -> !result.passed())
        .forEach(
            result ->
                summary
                    .append(System.lineSeparator())
                    .append("- ")
                    .append(result.scenarioId())
                    .append(": ")
                    .append(String.join("; ", result.failures())));
    return summary.toString();
  }

  public record ScenarioResult(String scenarioId, boolean passed, List<String> failures) {
    public ScenarioResult {
      failures = List.copyOf(failures);
    }
  }
}
