package com.aurora.agents;

import com.aurora.experiments.ExperimentProposal;
import com.aurora.experiments.ExperimentProposalRepository;
import com.aurora.objectives.WorkflowStage;
import com.aurora.objectives.WorkflowStageTimingService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class DeterministicExperimentationRuntime
    implements AgentRuntime<ExperimentationInput, ExperimentProposal> {
  private final AgentExecutionRepository executions;
  private final ExperimentProposalRepository proposals;
  private final WorkflowStageTimingService timings;
  private final ExperimentationAgent agent;

  public DeterministicExperimentationRuntime(
      AgentExecutionRepository executions,
      ExperimentProposalRepository proposals,
      WorkflowStageTimingService timings,
      AgentToolRegistry tools) {
    this.executions = executions;
    this.proposals = proposals;
    this.timings = timings;
    this.agent = new ExperimentationAgent(tools);
  }

  @Override
  public AgentRun<ExperimentProposal> run(ExperimentationInput input, String correlationId) {
    UUID executionId = UUID.randomUUID();
    Instant startedAt = Instant.now();
    executions.save(
        new AgentExecution(
            executionId,
            input.objective().objectiveId(),
            "EXPERIMENTATION",
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
      AgentResult<ExperimentProposal> result = agent.propose(input, executionId, correlationId);
      ExperimentProposal proposal = result.output();
      Instant completedAt = Instant.now();
      if (proposal != null) {
        try {
          proposals.save(proposal);
        } catch (DuplicateKeyException exception) {
          result =
              AgentResult.refused(
                  "PROPOSAL_ID_COLLISION",
                  "A proposal for this insight already exists; new evidence must produce a distinct insight before retrying",
                  java.util.Map.of("experimentId", proposal.experimentId()));
          proposal = null;
        }
      }
      AgentExecution execution =
          new AgentExecution(
              executionId,
              input.objective().objectiveId(),
              "EXPERIMENTATION",
              "deterministic",
              "1",
              startedAt,
              completedAt,
              result.refusal() == null ? "SUCCEEDED" : "REFUSED",
              0,
              0,
              BigDecimal.ZERO,
              Duration.between(startedAt, completedAt).toMillis(),
              result.refusal() == null ? proposal : result.refusal(),
              List.of(),
              List.of(),
              correlationId);
      executions.save(execution);
      timings.record(
          input.objective().objectiveId(),
          WorkflowStage.EXPERIMENT_DESIGN,
          execution.latencyMilliseconds(),
          "deterministic-experimentation-agent",
          startedAt,
          completedAt);
      return new AgentRun<>(
          proposal,
          executions
              .findById(executionId)
              .orElseThrow(() -> new IllegalStateException("Agent execution was not persisted")));
    } catch (RuntimeException exception) {
      Instant completedAt = Instant.now();
      executions.save(
          new AgentExecution(
              executionId,
              input.objective().objectiveId(),
              "EXPERIMENTATION",
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
