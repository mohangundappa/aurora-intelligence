package com.aurora.agents;

public record AgentResult<T>(T output, AgentRefusal refusal) {
  public AgentResult {
    if ((output == null) == (refusal == null)) {
      throw new IllegalArgumentException("agent result must contain exactly one output or refusal");
    }
  }

  public static <T> AgentResult<T> success(T output) {
    return new AgentResult<>(output, null);
  }

  public static <T> AgentResult<T> refused(String code, String reason) {
    return new AgentResult<>(null, new AgentRefusal(code, reason, java.util.Map.of()));
  }

  public static <T> AgentResult<T> refused(
      String code, String reason, java.util.Map<String, Object> details) {
    return new AgentResult<>(null, new AgentRefusal(code, reason, details));
  }
}
