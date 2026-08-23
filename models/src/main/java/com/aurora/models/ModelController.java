package com.aurora.models;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

  @PostMapping("/{name}/candidates")
  @ResponseStatus(HttpStatus.CREATED)
  public CandidateRegistration registerCandidate(
      @PathVariable String name,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody JsonNode body) {
    return models.registerCandidate(name, idempotencyKey, body);
  }

  @GetMapping("/{name}/candidates")
  public List<ModelCandidate> candidates(@PathVariable String name) {
    return models.candidates(name);
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

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> invalid(IllegalArgumentException exception) {
    return Map.of("error", exception.getMessage());
  }
}
