package com.aurora.agents;

import java.util.Map;

public record AgentRefusal(String code, String reason, Map<String, Object> details) {
  public AgentRefusal {
    if (code == null || code.isBlank())
      throw new IllegalArgumentException("refusal code is required");
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("refusal reason is required");
    }
    details = details == null ? Map.of() : Map.copyOf(details);
  }
}
