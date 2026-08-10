package com.aurora.experiments;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {
  private final ExperimentService experiments;
  private final ExperimentDefinitionService definitions;

  public ExperimentController(ExperimentService experiments) {
    this(experiments, null);
  }

  @Autowired
  public ExperimentController(
      ExperimentService experiments, ExperimentDefinitionService definitions) {
    this.experiments = experiments;
    this.definitions = definitions;
  }

  @GetMapping
  public java.util.List<ExperimentDefinition> definitions() {
    return experiments.definitions();
  }

  @GetMapping("/{experimentId}/performance")
  public ExperimentPerformance performance(@PathVariable String experimentId) {
    return experiments.performance(experimentId);
  }

  @PostMapping("/{experimentId}/deploy")
  public ExperimentDefinition deploy(@PathVariable String experimentId) {
    return definitions.deploy(experimentId);
  }

  @ExceptionHandler(UnknownExperimentException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String unknownExperiment(UnknownExperimentException exception) {
    return exception.getMessage();
  }
}
