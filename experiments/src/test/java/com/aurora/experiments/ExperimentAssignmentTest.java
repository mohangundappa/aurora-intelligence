package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExperimentAssignmentTest {
  @Test
  void assignmentRemainsStableAcrossIdentityStitch() {
    String before = ExperimentAssignment.assign("anon-1", null, "welcome-v1");
    String after = ExperimentAssignment.assign("anon-1", "customer-1", "welcome-v1");
    assertThat(after).isEqualTo(before);
  }

  @Test
  void measurementWarnsBelowMinimumSample() {
    assertThat(Measurement.conversion(12, 4, 12, 3).insufficientSample()).isTrue();
  }
}
