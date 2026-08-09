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

    ExperimentPerformance result = new ExperimentService(jdbc).performance("exp");

    assertThat(result.control().exposed()).isEqualTo(59);
    assertThat(result.treatment().exposed()).isZero();
    assertThat(result.insufficientSample()).isTrue();
  }
}
