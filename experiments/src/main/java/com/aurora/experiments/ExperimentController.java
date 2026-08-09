package com.aurora.experiments;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {
  private final ExperimentService experiments;

  public ExperimentController(ExperimentService experiments) {
    this.experiments = experiments;
  }

  @GetMapping
  public java.util.List<ExperimentDefinition> definitions() {
    return experiments.definitions();
  }

  @GetMapping("/{experimentId}/performance")
  public ExperimentPerformance performance(@PathVariable String experimentId) {
    return experiments.performance(experimentId);
  }
}
