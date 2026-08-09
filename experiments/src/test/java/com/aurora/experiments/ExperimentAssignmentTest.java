package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExperimentAssignmentTest {
  private static final ExperimentDefinition TWO_ARM =
      new ExperimentDefinition(
          "destination-experience-v1",
          "Destination experience test",
          "description",
          java.util.List.of(
              new ExperimentDefinition.Variant("control", 50),
              new ExperimentDefinition.Variant("treatment", 50)),
          "BOOKING_COMPLETED",
          30,
          ExperimentDefinition.LifecycleStatus.DEPLOYED);

  @Test
  void assignmentRemainsStableAcrossIdentityStitch() {
    String before = ExperimentAssignment.assign("anon-1", null, TWO_ARM);
    String after = ExperimentAssignment.assign("anon-1", "customer-1", TWO_ARM);
    assertThat(after).isEqualTo(before);
  }

  @Test
  void threeDeclaredVariantsUseDeterministicAllocationRanges() {
    ExperimentDefinition definition =
        new ExperimentDefinition(
            "three-arm",
            "Three arm",
            "description",
            java.util.List.of(
                new ExperimentDefinition.Variant("control", 20),
                new ExperimentDefinition.Variant("treatment", 50),
                new ExperimentDefinition.Variant("holdout", 30)),
            "BOOKING_COMPLETED",
            30,
            ExperimentDefinition.LifecycleStatus.DRAFT);

    assertThat(ExperimentAssignment.assign("subject-1", null, definition))
        .isEqualTo(ExperimentAssignment.assign("subject-1", null, definition));
    java.util.Map<String, Long> counts =
        java.util.stream.IntStream.range(0, 10_000)
            .mapToObj(bucket -> ExperimentAssignment.assign("subject-" + bucket, null, definition))
            .collect(
                java.util.stream.Collectors.groupingBy(
                    variant -> variant, java.util.stream.Collectors.counting()));
    assertThat(counts.get("control")).isBetween(1_800L, 2_200L);
    assertThat(counts.get("treatment")).isBetween(4_500L, 5_500L);
    assertThat(counts.get("holdout")).isBetween(2_500L, 3_500L);
  }

  @Test
  void measurementWarnsBelowMinimumSample() {
    assertThat(Measurement.conversion(12, 4, 12, 3, 30).insufficientSample()).isTrue();
  }
}
