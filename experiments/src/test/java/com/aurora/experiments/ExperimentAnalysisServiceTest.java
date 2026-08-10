package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentAnalysisServiceTest {
  @Test
  void refusesUnknownExperimentBeforePersistingAnalysis() {
    ExperimentAnalysisRepository analyses = mock(ExperimentAnalysisRepository.class);
    ExperimentRegistry registry = mock(ExperimentRegistry.class);
    when(registry.definition("missing-experiment"))
        .thenThrow(new UnknownExperimentException("missing-experiment", List.of()));
    ExperimentAnalysisService service = new ExperimentAnalysisService(analyses, registry);

    ExperimentAnalysis analysis =
        new ExperimentAnalysis(
            UUID.randomUUID(),
            "missing-experiment",
            List.of(
                new ExperimentAnalysis.VariantResult("control", 40, 4, 0.1),
                new ExperimentAnalysis.VariantResult("treatment", 40, 5, 0.125)),
            true,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            ExperimentAnalysis.Recommendation.ITERATE,
            "Observed in this experiment.",
            List.of("fixture:evidence"),
            "correlation",
            Instant.now());

    assertThatThrownBy(() -> service.save(analysis))
        .isInstanceOf(UnknownExperimentException.class)
        .hasMessageContaining("missing-experiment");
    verify(analyses, never()).save(analysis);
  }
}
