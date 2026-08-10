package com.aurora.agents;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentExecution(
    UUID executionId,
    String objectiveId,
    String agentType,
    String model,
    String modelVersion,
    Instant startedAt,
    Instant completedAt,
    String status,
    Integer inputTokenCount,
    Integer outputTokenCount,
    BigDecimal estimatedCost,
    long latencyMilliseconds,
    Object output,
    List<AgentToolInvocation> toolCalls,
    List<String> errors,
    String correlationId) {
  public AgentExecution {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    errors = errors == null ? List.of() : List.copyOf(errors);
  }
}
