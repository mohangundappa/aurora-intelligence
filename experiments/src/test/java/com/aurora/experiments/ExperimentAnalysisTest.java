package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentAnalysisTest {
  @Test
  void insufficientAnalysisCannotContainLift() {
    assertThatThrownBy(
            () ->
                new ExperimentAnalysis(
                    UUID.randomUUID(),
                    "experiment",
                    variants(),
                    false,
                    BigDecimal.valueOf(0.1),
                    BigDecimal.valueOf(0.2),
                    ExperimentAnalysis.Recommendation.ITERATE,
                    "Keep running",
                    List.of("evidence"),
                    "correlation",
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot contain lift");
  }

  private List<ExperimentAnalysis.VariantResult> variants() {
    return List.of(
        new ExperimentAnalysis.VariantResult("control", 30, 3, 0.1),
        new ExperimentAnalysis.VariantResult("treatment", 30, 6, 0.2));
  }
}
