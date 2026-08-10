package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentProposalTest {
  @Test
  void rejectsProposalThatCannotBecomeAnExperimentDefinition() {
    assertThatThrownBy(
            () ->
                new ExperimentProposal(
                    UUID.randomUUID(),
                    "objective",
                    UUID.randomUUID(),
                    "experiment",
                    "Experiment",
                    "Description",
                    "Weekend leisure travelers",
                    "weekend-getaway-affinity",
                    "Hypothesis",
                    List.of(new ExperimentProposal.Variant("control", 60)),
                    "BOOKING_COMPLETED",
                    30,
                    BigDecimal.ZERO,
                    "Reasoning",
                    List.of("evidence"),
                    "correlation",
                    ExperimentProposal.GovernanceState.PROPOSED,
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sum to 100");
  }

  @Test
  void convertsToDraftDefinitionOnly() {
    ExperimentProposal proposal = proposal();

    assertThat(proposal.toDraftDefinition().lifecycleStatus())
        .isEqualTo(ExperimentDefinition.LifecycleStatus.DRAFT);
  }

  private ExperimentProposal proposal() {
    return new ExperimentProposal(
        UUID.randomUUID(),
        "objective",
        UUID.randomUUID(),
        "experiment",
        "Experiment",
        "Description",
        "Weekend leisure travelers",
        "weekend-getaway-affinity",
        "Hypothesis",
        List.of(
            new ExperimentProposal.Variant("treatment", 20),
            new ExperimentProposal.Variant("control", 80)),
        "BOOKING_COMPLETED",
        30,
        BigDecimal.valueOf(0.1),
        "Reasoning",
        List.of("evidence"),
        "correlation",
        ExperimentProposal.GovernanceState.PROPOSED,
        Instant.now());
  }
}
