package com.aurora.experiments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExperimentProposal(
    UUID proposalId,
    String objectiveId,
    UUID insightId,
    String experimentId,
    String experimentName,
    String experimentDescription,
    String hypothesis,
    List<Variant> variants,
    String primaryOutcomeEvent,
    int minimumExposuresPerVariant,
    BigDecimal expectedEffect,
    String reasoning,
    List<String> evidenceRefs,
    String correlationId,
    GovernanceState governanceState,
    Instant createdAt) {
  public ExperimentProposal {
    if (proposalId == null) throw new IllegalArgumentException("proposalId is required");
    if (objectiveId == null || objectiveId.isBlank()) {
      throw new IllegalArgumentException("objectiveId is required");
    }
    if (insightId == null) throw new IllegalArgumentException("insightId is required");
    if (experimentId == null || experimentId.isBlank()) {
      throw new IllegalArgumentException("experimentId is required");
    }
    if (experimentName == null || experimentName.isBlank()) {
      throw new IllegalArgumentException("experimentName is required");
    }
    if (experimentDescription == null || experimentDescription.isBlank()) {
      throw new IllegalArgumentException("experimentDescription is required");
    }
    if (hypothesis == null || hypothesis.isBlank()) {
      throw new IllegalArgumentException("hypothesis is required");
    }
    if (variants == null || variants.isEmpty()) {
      throw new IllegalArgumentException("variants are required");
    }
    if (primaryOutcomeEvent == null || primaryOutcomeEvent.isBlank()) {
      throw new IllegalArgumentException("primaryOutcomeEvent is required");
    }
    if (minimumExposuresPerVariant <= 0) {
      throw new IllegalArgumentException("minimumExposuresPerVariant must be positive");
    }
    if (expectedEffect == null || expectedEffect.signum() < 0) {
      throw new IllegalArgumentException("expectedEffect must be zero or greater");
    }
    if (reasoning == null || reasoning.isBlank()) {
      throw new IllegalArgumentException("reasoning is required");
    }
    if (evidenceRefs == null || evidenceRefs.isEmpty()) {
      throw new IllegalArgumentException("evidenceRefs are required");
    }
    if (correlationId == null || correlationId.isBlank()) {
      throw new IllegalArgumentException("correlationId is required");
    }
    if (governanceState == null) throw new IllegalArgumentException("governanceState is required");
    if (createdAt == null) throw new IllegalArgumentException("createdAt is required");
    if (variants.stream().map(Variant::name).distinct().count() != variants.size()) {
      throw new IllegalArgumentException("proposal variants must be unique");
    }
    if (variants.stream().anyMatch(variant -> variant.name() == null || variant.name().isBlank())) {
      throw new IllegalArgumentException("proposal variant names are required");
    }
    if (variants.stream().anyMatch(variant -> variant.allocationPercentage() <= 0)) {
      throw new IllegalArgumentException("proposal variant allocations must be positive");
    }
    if (variants.stream().mapToInt(Variant::allocationPercentage).sum() != 100) {
      throw new IllegalArgumentException("proposal variant allocations must sum to 100");
    }
    variants = List.copyOf(variants);
    evidenceRefs = List.copyOf(evidenceRefs);
  }

  public ExperimentDefinition toDraftDefinition() {
    return new ExperimentDefinition(
        experimentId,
        experimentName,
        experimentDescription,
        variants.stream()
            .map(
                variant ->
                    new ExperimentDefinition.Variant(
                        variant.name(), variant.allocationPercentage()))
            .toList(),
        primaryOutcomeEvent,
        minimumExposuresPerVariant,
        ExperimentDefinition.LifecycleStatus.DRAFT);
  }

  public record Variant(String name, int allocationPercentage) {}

  public enum GovernanceState {
    PROPOSED,
    APPROVED,
    ACTIVATED,
    REJECTED
  }
}
