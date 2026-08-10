package com.aurora.experiments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExperimentAnalysis(
    UUID analysisId,
    String experimentId,
    List<VariantResult> variants,
    boolean sufficientSample,
    BigDecimal absoluteLift,
    BigDecimal relativeLift,
    Recommendation recommendation,
    String reasoning,
    List<String> evidenceRefs,
    String correlationId,
    Instant producedAt) {
  public ExperimentAnalysis {
    if (analysisId == null) throw new IllegalArgumentException("analysisId is required");
    if (experimentId == null || experimentId.isBlank()) {
      throw new IllegalArgumentException("experimentId is required");
    }
    if (variants == null || variants.size() < 2) {
      throw new IllegalArgumentException("at least two variant results are required");
    }
    if (variants.stream().anyMatch(variant -> variant == null)) {
      throw new IllegalArgumentException("variant results are required");
    }
    if (variants.stream().map(VariantResult::variant).distinct().count() != variants.size()) {
      throw new IllegalArgumentException("variant results must be unique");
    }
    if (variants.stream()
        .anyMatch(
            variant ->
                variant.exposures() < 0
                    || variant.outcomes() < 0
                    || variant.conversionRate() < 0
                    || variant.conversionRate() > 1)) {
      throw new IllegalArgumentException("variant measurements are invalid");
    }
    if (sufficientSample && (absoluteLift == null || relativeLift == null)) {
      throw new IllegalArgumentException("sufficient analyses require lift measurements");
    }
    if (!sufficientSample && (absoluteLift != null || relativeLift != null)) {
      throw new IllegalArgumentException("insufficient analyses cannot contain lift measurements");
    }
    if (recommendation == null) {
      throw new IllegalArgumentException("recommendation is required");
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
    if (producedAt == null) throw new IllegalArgumentException("producedAt is required");
    variants = List.copyOf(variants);
    evidenceRefs = List.copyOf(evidenceRefs);
  }

  public record VariantResult(String variant, int exposures, int outcomes, double conversionRate) {
    public VariantResult {
      if (variant == null || variant.isBlank())
        throw new IllegalArgumentException("variant is required");
    }
  }

  public enum Recommendation {
    SHIP,
    STOP,
    ITERATE
  }
}
