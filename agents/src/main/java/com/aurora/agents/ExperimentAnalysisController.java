package com.aurora.agents;

import com.aurora.experiments.ExperimentAnalysis;
import com.aurora.experiments.ExperimentAnalysisService;
import com.aurora.experiments.UnknownExperimentAnalysisException;
import com.aurora.experiments.UnknownExperimentException;
import com.aurora.objectives.MarketingObjectiveService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ExperimentAnalysisController {
  private final ExperimentAnalysisService analyses;
  private final DeterministicAnalyticsRuntime runtime;
  private final MarketingObjectiveService objectives;

  public ExperimentAnalysisController(
      ExperimentAnalysisService analyses,
      DeterministicAnalyticsRuntime runtime,
      MarketingObjectiveService objectives) {
    this.analyses = analyses;
    this.runtime = runtime;
    this.objectives = objectives;
  }

  @GetMapping("/experiments/{experimentId}/analyses")
  public List<ExperimentAnalysis> list(@PathVariable String experimentId) {
    return analyses.list(experimentId);
  }

  @GetMapping("/experiment-analyses/{analysisId}")
  public ExperimentAnalysis get(@PathVariable UUID analysisId) {
    return analyses.get(analysisId);
  }

  @PostMapping("/experiments/{experimentId}/analyses")
  public AgentExecution analyze(
      @PathVariable String experimentId, @RequestBody AnalysisRequest request) {
    if (request == null || request.objectiveId() == null || request.objectiveId().isBlank()) {
      throw new IllegalArgumentException("objectiveId is required");
    }
    if (request.correlationId() == null || request.correlationId().isBlank()) {
      throw new IllegalArgumentException("correlationId is required");
    }
    objectives.get(request.objectiveId());
    return runtime
        .run(new AnalyticsInput(request.objectiveId(), experimentId), request.correlationId())
        .execution();
  }

  @ExceptionHandler(UnknownExperimentException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> unknownExperiment(UnknownExperimentException exception) {
    return Map.of("error", exception.getMessage());
  }

  @ExceptionHandler(UnknownExperimentAnalysisException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> unknownAnalysis(UnknownExperimentAnalysisException exception) {
    return Map.of("error", exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> invalid(IllegalArgumentException exception) {
    return Map.of("error", exception.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> conflict(IllegalStateException exception) {
    return Map.of("error", exception.getMessage());
  }

  public record AnalysisRequest(String objectiveId, String correlationId) {}
}
