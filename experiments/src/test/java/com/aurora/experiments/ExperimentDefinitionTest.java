package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExperimentDefinitionTest {
  @Test
  void rejectsAllocationsThatDoNotSumToOneHundred() {
    assertThatThrownBy(
            () ->
                new ExperimentDefinition(
                    "invalid",
                    "Invalid",
                    "description",
                    List.of(
                        new ExperimentDefinition.Variant("control", 60),
                        new ExperimentDefinition.Variant("treatment", 30)),
                    "BOOKING_COMPLETED",
                    30,
                    ExperimentDefinition.LifecycleStatus.DRAFT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("allocations must sum to 100");
  }
}
