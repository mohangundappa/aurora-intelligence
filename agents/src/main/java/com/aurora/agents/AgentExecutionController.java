package com.aurora.agents;

import com.aurora.objectives.MarketingObjective;
import com.aurora.objectives.MarketingObjectiveService;
import java.util.List;
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

  public AgentExecutionController(
      AgentExecutionRepository executions,
      MarketingObjectiveService objectives,
      DeterministicAgentRuntime runtime) {
    this.executions = executions;
    this.objectives = objectives;
    this.runtime = runtime;
  }

  @PostMapping("/objectives/{objectiveId}/insights")
  public AgentExecution runInsights(@PathVariable String objectiveId) {
    MarketingObjective objective = objectives.get(objectiveId);
    return runtime.run(objective, UUID.randomUUID().toString()).execution();
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
}
