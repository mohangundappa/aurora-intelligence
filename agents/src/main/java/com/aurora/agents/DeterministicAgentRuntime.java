package com.aurora.agents;

import com.aurora.objectives.MarketingObjective;
import com.aurora.objectives.WorkflowStage;
import com.aurora.objectives.WorkflowStageTimingService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeterministicAgentRuntime
    implements AgentRuntime<MarketingObjective, MarketingInsight> {
  private final AgentExecutionRepository executions;
  private final MarketingInsightRepository insights;
  private final WorkflowStageTimingService timings;
  private final InsightsAgent insightsAgent;

  public DeterministicAgentRuntime(
      AgentExecutionRepository executions,
      MarketingInsightRepository insights,
      WorkflowStageTimingService timings,
      AgentToolRegistry tools) {
    this.executions = executions;
    this.insights = insights;
    this.timings = timings;
    this.insightsAgent = new InsightsAgent(tools);
  }

  @Override
  public AgentRun<MarketingInsight> run(MarketingObjective objective, String correlationId) {
    UUID executionId = UUID.randomUUID();
    Instant startedAt = Instant.now();
    executions.save(
        new AgentExecution(
            executionId,
            objective.objectiveId(),
            "INSIGHTS",
            "deterministic",
            "1",
            startedAt,
            startedAt,
            "RUNNING",
            0,
            0,
            BigDecimal.ZERO,
            0,
            null,
            List.of(),
            List.of(),
            correlationId));
    try {
      MarketingInsight insight = insightsAgent.derive(objective, executionId, correlationId);
      Instant completedAt = Instant.now();
      if (insight != null) insights.save(insight);
      AgentExecution execution =
          new AgentExecution(
              executionId,
              objective.objectiveId(),
              "INSIGHTS",
              "deterministic",
              "1",
              startedAt,
              completedAt,
              "SUCCEEDED",
              0,
              0,
              BigDecimal.ZERO,
              Duration.between(startedAt, completedAt).toMillis(),
              insight,
              List.of(),
              List.of(),
              correlationId);
      executions.save(execution);
      timings.record(
          objective.objectiveId(),
          WorkflowStage.INSIGHT_GENERATION,
          execution.latencyMilliseconds(),
          "deterministic-insights-agent",
          startedAt,
          completedAt);
      return new AgentRun<>(
          insight,
          executions
              .findById(executionId)
              .orElseThrow(() -> new IllegalStateException("Agent execution was not persisted")));
    } catch (RuntimeException exception) {
      Instant completedAt = Instant.now();
      AgentExecution execution =
          new AgentExecution(
              executionId,
              objective.objectiveId(),
              "INSIGHTS",
              "deterministic",
              "1",
              startedAt,
              completedAt,
              "FAILED",
              0,
              0,
              BigDecimal.ZERO,
              Duration.between(startedAt, completedAt).toMillis(),
              null,
              List.of(),
              List.of(
                  exception.getMessage() == null ? exception.toString() : exception.getMessage()),
              correlationId);
      executions.save(execution);
      throw exception;
    }
  }
}
