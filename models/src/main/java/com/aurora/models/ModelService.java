package com.aurora.models;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ModelService {
  private final ModelRepository repository;

  public ModelService(ModelRepository repository) {
    this.repository = repository;
  }

  public List<ModelVersion> versions(String name) {
    return repository.findAll(name);
  }

  public void approve(String name, String version, String actor) {
    repository.transition(name, version, "APPROVED", actor);
  }

  public void deploy(String name, String version, String actor) {
    repository.transition(name, version, "DEPLOYED", actor);
  }

  public void rollback(String name, String version, String actor) {
    repository.transition(name, version, "DEPLOYED", actor);
  }

  public List<Map<String, Object>> audit(String name) {
    return repository.audit(name);
  }

  public Evaluation evaluate(String name, String version) {
    ModelVersion model =
        repository.findAll(name).stream()
            .filter(candidate -> candidate.version().equals(version))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown model version " + version));
    double[][] rows = {{1, 0, 0, 0}, {1, 1, 1, 0}, {1, 1, 1, 1}, {0, 0, 0, 1}};
    double[] labels = {25, 60, 78, 46};
    double error = 0;
    int correct = 0;
    for (int index = 0; index < rows.length; index++) {
      Map<String, Double> features =
          Map.of(
              "propertyViewed", rows[index][0],
              "roomViewed", rows[index][1],
              "rateViewed", rows[index][2],
              "bookingStarted", rows[index][3]);
      double score = model.bias();
      for (String feature : model.features()) {
        score += features.getOrDefault(feature, 0d) * model.weights().getOrDefault(feature, 0d);
      }
      error += Math.abs(Math.max(0, Math.min(100, score)) - labels[index]);
      if ((score >= 50) == (labels[index] >= 50)) {
        correct++;
      }
    }
    return new Evaluation(
        name, version, rows.length, (double) correct / rows.length, error / rows.length);
  }

  public Prediction predict(String name, Map<String, Double> features) {
    long started = System.nanoTime();
    ModelVersion model = repository.findDeployed(name);
    double score = model.bias();
    Map<String, Double> contributions = new LinkedHashMap<>();
    for (String feature : model.features()) {
      double contribution =
          features.getOrDefault(feature, 0d) * model.weights().getOrDefault(feature, 0d);
      contributions.put(feature, contribution);
      score += contribution;
    }
    score = Math.max(0, Math.min(100, score));
    return new Prediction(
        name,
        model.version(),
        score,
        contributions,
        "Score uses deployed "
            + name
            + " version "
            + model.version()
            + "; feature contributions explain the result.",
        (System.nanoTime() - started) / 1_000_000d);
  }
}
