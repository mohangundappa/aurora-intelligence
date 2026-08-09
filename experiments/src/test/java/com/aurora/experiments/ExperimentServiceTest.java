package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ExperimentServiceTest {
  @Test
  void missingVariantIsInsufficientEvenWhenOtherVariantHasExposure() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(
            contains("count(*) from experiment_exposures where experiment_id=? and variant=?"),
            eq(Integer.class),
            eq("exp"),
            eq("control")))
        .thenReturn(59);
    when(jdbc.queryForObject(
            contains("count(*) from experiment_exposures where experiment_id=? and variant=?"),
            eq(Integer.class),
            eq("exp"),
            eq("treatment")))
        .thenReturn(0);
    when(jdbc.queryForObject(
            contains("select count(*) from experiment_outcomes"),
            eq(Integer.class),
            any(),
            any(),
            any()))
        .thenReturn(0);

    ExperimentRegistry registry = mock(ExperimentRegistry.class);
    ExperimentDefinition definition =
        new ExperimentDefinition(
            "exp",
            "Experiment",
            "description",
            java.util.List.of(
                new ExperimentDefinition.Variant("control", 50),
                new ExperimentDefinition.Variant("treatment", 50)),
            "BOOKING_COMPLETED",
            30,
            ExperimentDefinition.LifecycleStatus.DEPLOYED);
    when(registry.definition("exp")).thenReturn(definition);

    ExperimentPerformance result = new ExperimentService(jdbc, registry).performance("exp");

    assertThat(result.variants())
        .extracting(ExperimentPerformance.Variant::name)
        .containsExactly("control", "treatment");
    assertThat(result.variants().get(0).exposed()).isEqualTo(59);
    assertThat(result.variants().get(1).exposed()).isZero();
    assertThat(result.insufficientSample()).isTrue();
  }
}
