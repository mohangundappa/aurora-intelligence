package com.aurora.models;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/models")
public class ModelController {
  private final ModelService models;

  public ModelController(ModelService models) {
    this.models = models;
  }

  @GetMapping("/{name}")
  public java.util.List<ModelVersion> versions(@PathVariable String name) {
    return models.versions(name);
  }

  @GetMapping("/{name}/audit")
  public java.util.List<Map<String, Object>> audit(@PathVariable String name) {
    return models.audit(name);
  }

  @GetMapping("/{name}/{version}/evaluation")
  public Evaluation evaluate(@PathVariable String name, @PathVariable String version) {
    return models.evaluate(name, version);
  }

  @PostMapping("/{name}/{version}/approve")
  public void approve(@PathVariable String name, @PathVariable String version) {
    models.approve(name, version, "console-presenter");
  }

  @PostMapping("/{name}/{version}/deploy")
  public void deploy(@PathVariable String name, @PathVariable String version) {
    models.deploy(name, version, "console-presenter");
  }

  @PostMapping("/{name}/{version}/rollback")
  public void rollback(@PathVariable String name, @PathVariable String version) {
    models.rollback(name, version, "console-presenter");
  }

  @PostMapping("/{name}/predict")
  public Prediction predict(@PathVariable String name, @RequestBody Map<String, Double> features) {
    return models.predict(name, features);
  }
}
