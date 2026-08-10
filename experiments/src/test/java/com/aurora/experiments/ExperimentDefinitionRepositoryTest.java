package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ExperimentDefinitionRepositoryTest {
  @Test
  void invalidDatabaseRowIsRejectedByDefinitionValidation() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(any(String.class), any(RowMapper.class)))
        .thenReturn(
            List.of(
                new ExperimentDefinitionRepository.ExperimentDefinitionRow(
                    "invalid", "Invalid", "Invalid", "BOOKING_COMPLETED", 30, "DEPLOYED")))
        .thenReturn(
            List.of(new ExperimentDefinitionRepository.VariantRow("invalid", "control", 50)));

    assertThatThrownBy(() -> new ExperimentDefinitionRepository(jdbc).findAll())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("allocations must sum to 100");
  }
}
