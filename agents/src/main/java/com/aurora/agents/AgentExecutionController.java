package com.aurora.agents;

import com.aurora.objectives.MarketingObjective;
import com.aurora.objectives.MarketingObjectiveService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AgentExecutionController {
  private final AgentExecutionRepository executions;
  private final MarketingObjectiveService objectives;
  private final DeterministicAgentRuntime runtime;
  private final DeterministicExperimentationRuntime experimentation;
  private final MarketingInsightRepository insights;

  public AgentExecutionController(
      AgentExecutionRepository executions,
      MarketingObjectiveService objectives,
      DeterministicAgentRuntime runtime,
      DeterministicExperimentationRuntime experimentation,
      MarketingInsightRepository insights) {
    this.executions = executions;
    this.objectives = objectives;
    this.runtime = runtime;
    this.experimentation = experimentation;
    this.insights = insights;
  }

  @PostMapping("/objectives/{objectiveId}/insights")
  public AgentExecution runInsights(@PathVariable String objectiveId) {
    MarketingObjective objective = objectives.get(objectiveId);
    return runtime.run(objective, UUID.randomUUID().toString()).execution();
  }

  @PostMapping("/objectives/{objectiveId}/experiment-proposals")
  public AgentExecution runExperimentation(
      @PathVariable String objectiveId,
      @org.springframework.web.bind.annotation.RequestBody ProposalRequest request) {
    MarketingObjective objective = objectives.get(objectiveId);
    MarketingInsight insight =
        insights
            .findById(request.insightId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown marketing insight " + request.insightId()));
    if (!objective.objectiveId().equals(insight.objectiveId())) {
      throw new IllegalArgumentException("insight must belong to the objective");
    }
    return experimentation
        .run(new ExperimentationInput(objective, insight), UUID.randomUUID().toString())
        .execution();
  }

  @GetMapping("/agent-executions")
  public List<AgentExecution> list() {
    return executions.findAll();
  }

  @GetMapping("/agent-executions/{executionId}")
  public AgentExecution get(@PathVariable UUID executionId) {
    return executions
        .findById(executionId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Unknown agent execution " + executionId));
  }

  public record ProposalRequest(UUID insightId) {}

  @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> invalid(IllegalArgumentException exception) {
    return Map.of("error", exception.getMessage());
  }
}
