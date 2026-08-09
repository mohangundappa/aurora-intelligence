package com.aurora.experiments;

import java.util.List;

public record ExperimentDefinition(
    String id,
    String name,
    String description,
    List<Variant> variants,
    String primaryOutcomeEvent,
    int minimumExposuresPerVariant,
    LifecycleStatus lifecycleStatus) {
  public ExperimentDefinition {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Experiment id is required");
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Experiment name is required");
    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException("Experiment description is required for " + id);
    }
    if (variants == null || variants.isEmpty()) {
      throw new IllegalArgumentException("Experiment " + id + " must declare variants");
    }
    if (primaryOutcomeEvent == null || primaryOutcomeEvent.isBlank()) {
      throw new IllegalArgumentException(
          "Experiment " + id + " must declare a primary outcome event");
    }
    if (minimumExposuresPerVariant <= 0) {
      throw new IllegalArgumentException(
          "Experiment " + id + " minimum exposures must be positive");
    }
    if (lifecycleStatus == null) {
      throw new IllegalArgumentException("Experiment " + id + " lifecycle status is required");
    }
    long allocationTotal =
        variants.stream().mapToLong(variant -> variant.allocationPercentage()).sum();
    if (allocationTotal != 100) {
      throw new IllegalArgumentException(
          "Experiment " + id + " allocations must sum to 100, got " + allocationTotal);
    }
    if (variants.stream().map(Variant::name).distinct().count() != variants.size()) {
      throw new IllegalArgumentException("Experiment " + id + " variants must be unique");
    }
    if (variants.stream().anyMatch(variant -> variant.name() == null || variant.name().isBlank())) {
      throw new IllegalArgumentException("Experiment " + id + " variant names are required");
    }
    if (variants.stream().anyMatch(variant -> variant.allocationPercentage() <= 0)) {
      throw new IllegalArgumentException(
          "Experiment " + id + " variant allocations must be positive");
    }
    variants = List.copyOf(variants);
  }

  public record Variant(String name, int allocationPercentage) {}

  public enum LifecycleStatus {
    DRAFT,
    TESTED,
    APPROVED,
    DEPLOYED,
    RETIRED
  }
}
