package com.aurora.agents;

import com.aurora.experiments.ExperimentAnalysis;
import com.aurora.experiments.ExperimentAnalysisService;
import com.aurora.objectives.WorkflowStage;
import com.aurora.objectives.WorkflowStageTimingService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeterministicAnalyticsRuntime
    implements AgentRuntime<AnalyticsInput, ExperimentAnalysis> {
  private final AgentExecutionRepository executions;
  private final ExperimentAnalysisService analyses;
  private final WorkflowStageTimingService timings;
  private final AnalyticsAgent agent;

  public DeterministicAnalyticsRuntime(
      AgentExecutionRepository executions,
      ExperimentAnalysisService analyses,
      WorkflowStageTimingService timings,
      AgentToolRegistry tools) {
    this.executions = executions;
    this.analyses = analyses;
    this.timings = timings;
    this.agent = new AnalyticsAgent(tools);
  }

  @Override
  public AgentRun<ExperimentAnalysis> run(AnalyticsInput input, String correlationId) {
    UUID executionId = UUID.randomUUID();
    Instant startedAt = Instant.now();
    executions.save(
        new AgentExecution(
            executionId,
            input.objectiveId(),
            "ANALYTICS",
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
      AgentResult<ExperimentAnalysis> result = agent.analyze(input, executionId, correlationId);
      ExperimentAnalysis analysis = result.output();
      Instant completedAt = Instant.now();
      if (analysis != null) analyses.save(analysis);
      AgentExecution execution =
          new AgentExecution(
              executionId,
              input.objectiveId(),
              "ANALYTICS",
              "deterministic",
              "1",
              startedAt,
              completedAt,
              result.refusal() == null ? "SUCCEEDED" : "REFUSED",
              0,
              0,
              BigDecimal.ZERO,
              Duration.between(startedAt, completedAt).toMillis(),
              result.refusal() == null ? analysis : result.refusal(),
              List.of(),
              List.of(),
              correlationId);
      executions.save(execution);
      timings.record(
          input.objectiveId(),
          WorkflowStage.ANALYSIS,
          execution.latencyMilliseconds(),
          "deterministic-analytics-agent",
          startedAt,
          completedAt);
      return new AgentRun<>(
          analysis,
          executions
              .findById(executionId)
              .orElseThrow(() -> new IllegalStateException("Agent execution was not persisted")));
    } catch (RuntimeException exception) {
      Instant completedAt = Instant.now();
      executions.save(
          new AgentExecution(
              executionId,
              input.objectiveId(),
              "ANALYTICS",
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
              correlationId));
      throw exception;
    }
  }
}
