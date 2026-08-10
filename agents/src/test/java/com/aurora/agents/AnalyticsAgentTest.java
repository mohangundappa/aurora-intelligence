package com.aurora.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurora.experiments.ExperimentAnalysis;
import com.aurora.experiments.ExperimentPerformance;
import com.aurora.experiments.ExperimentService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticsAgentTest {
  private static final UUID EXECUTION_ID = UUID.randomUUID();

  @Test
  void derivesBoundedAnalysisFromSeededOutcomeEvidence() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    stubEvidence(tools, 40, 32);

    AgentResult<ExperimentAnalysis> result =
        new AnalyticsAgent(tools)
            .analyze(
                new AnalyticsInput("objective", "destination-experience-v1"), EXECUTION_ID, "corr");

    assertThat(result.refusal()).isNull();
    assertThat(result.output()).isNotNull();
    assertThat(result.output().sufficientSample()).isTrue();
    assertThat(result.output().variants())
        .extracting(ExperimentAnalysis.VariantResult::exposures)
        .containsExactly(40, 32);
    assertThat(result.output().absoluteLift()).isNotNull();
    assertThat(result.output().reasoning())
        .contains(
            "Observed in this experiment",
            "two-sided 5%",
            "80% power",
            "minimum detectable effect",
            "no guardrail metrics were examined")
        .doesNotContain("80% power planning assumption");
    assertThat(result.output().recommendation())
        .isIn(
            ExperimentAnalysis.Recommendation.SHIP,
            ExperimentAnalysis.Recommendation.STOP,
            ExperimentAnalysis.Recommendation.ITERATE);
  }

  @Test
  void refusesWhenEitherArmIsBelowThePlatformMinimumWithoutLift() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    stubEvidence(tools, 29, 40);

    AgentResult<ExperimentAnalysis> result =
        new AnalyticsAgent(tools)
            .analyze(new AnalyticsInput("objective", "experiment"), EXECUTION_ID, "corr");

    assertThat(result.refusal()).extracting(AgentRefusal::code).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(result.output()).isNull();
    assertThat(result.refusal().details()).containsKey("evidenceRefs");
  }

  @Test
  void refusesWhenAnArmHasZeroExposures() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    when(tools.invoke(
            "getExperimentPerformance", new AgentToolInputs.Experiment("experiment"), EXECUTION_ID))
        .thenReturn(
            invocation(
                "performance",
                new ExperimentPerformance(
                    "experiment",
                    "Experiment",
                    "Description",
                    "BOOKING_COMPLETED",
                    30,
                    List.of(
                        new ExperimentPerformance.Variant("control", 0, 0, 0, 0, 0),
                        new ExperimentPerformance.Variant("treatment", 40, 0, 0, 20, 0.5)),
                    true,
                    "insufficient")));
    when(tools.invoke(
            "getExperimentExposures", new AgentToolInputs.Experiment("experiment"), EXECUTION_ID))
        .thenReturn(invocation("exposures", List.of()));
    when(tools.invoke(
            "getExperimentOutcomes", new AgentToolInputs.Experiment("experiment"), EXECUTION_ID))
        .thenReturn(invocation("outcomes", List.of()));

    AgentResult<ExperimentAnalysis> result =
        new AnalyticsAgent(tools)
            .analyze(new AnalyticsInput("objective", "experiment"), EXECUTION_ID, "corr");

    assertThat(result.refusal()).extracting(AgentRefusal::code).isEqualTo("ZERO_EXPOSURES");
    assertThat(result.output()).isNull();
  }

  @Test
  void insufficientSampleCannotProduceLiftOrShip() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    stubEvidence(tools, 10, 10);

    AgentResult<ExperimentAnalysis> result =
        new AnalyticsAgent(tools)
            .analyze(new AnalyticsInput("objective", "experiment"), EXECUTION_ID, "corr");

    assertThat(result.refusal()).isNotNull();
    assertThat(result.refusal().code()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(result.output()).isNull();
  }

  @Test
  void refusesMultiArmExperimentInsteadOfDroppingAnArm() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    when(tools.invoke(
            "getExperimentPerformance", new AgentToolInputs.Experiment("multi-arm"), EXECUTION_ID))
        .thenReturn(
            invocation(
                "performance",
                new ExperimentPerformance(
                    "multi-arm",
                    "Experiment",
                    "Description",
                    "BOOKING_COMPLETED",
                    30,
                    List.of(
                        new ExperimentPerformance.Variant("arm-a", 40, 0, 0, 10, 0.25),
                        new ExperimentPerformance.Variant("arm-b", 40, 0, 0, 12, 0.3),
                        new ExperimentPerformance.Variant("arm-c", 40, 0, 0, 15, 0.375)),
                    false,
                    "")));
    when(tools.invoke(
            "getExperimentExposures", new AgentToolInputs.Experiment("multi-arm"), EXECUTION_ID))
        .thenReturn(invocation("exposures", List.of()));
    when(tools.invoke(
            "getExperimentOutcomes", new AgentToolInputs.Experiment("multi-arm"), EXECUTION_ID))
        .thenReturn(invocation("outcomes", List.of()));

    AgentResult<ExperimentAnalysis> result =
        new AnalyticsAgent(tools)
            .analyze(new AnalyticsInput("objective", "multi-arm"), EXECUTION_ID, "corr");

    assertThat(result.refusal()).extracting(AgentRefusal::code).isEqualTo("MULTI_ARM_UNSUPPORTED");
    assertThat(result.refusal().reason()).contains("multiple-comparison handling");
    assertThat(result.output()).isNull();
  }

  @Test
  void namesBaselineWhenNeitherArmUsesControlOrTreatment() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    stubEvidence(tools, 40, 32, "baseline-arm", "variant-arm");

    AgentResult<ExperimentAnalysis> result =
        new AnalyticsAgent(tools)
            .analyze(
                new AnalyticsInput("objective", "destination-experience-v1"), EXECUTION_ID, "corr");

    assertThat(result.refusal()).isNull();
    assertThat(result.output().reasoning())
        .contains("No arm was named 'control', so the first declared arm baseline-arm");
  }

  private void stubEvidence(AgentToolRegistry tools, int controlExposures, int treatmentExposures) {
    stubEvidence(tools, controlExposures, treatmentExposures, "control", "treatment");
  }

  private void stubEvidence(
      AgentToolRegistry tools,
      int controlExposures,
      int treatmentExposures,
      String controlName,
      String treatmentName) {
    String experimentId = "destination-experience-v1";
    when(tools.invoke(
            "getExperimentPerformance", new AgentToolInputs.Experiment(experimentId), EXECUTION_ID))
        .thenReturn(
            invocation(
                "performance",
                new ExperimentPerformance(
                    experimentId,
                    "Experiment",
                    "Description",
                    "BOOKING_COMPLETED",
                    30,
                    List.of(
                        new ExperimentPerformance.Variant(
                            controlName, controlExposures, 0, 0, 10, 0.25),
                        new ExperimentPerformance.Variant(
                            treatmentName, treatmentExposures, 0, 0, 20, 0.5)),
                    false,
                    "")));
    List<ExperimentService.Exposure> exposures =
        java.util.stream.Stream.concat(
                java.util.stream.IntStream.range(0, controlExposures)
                    .mapToObj(
                        index ->
                            new ExperimentService.Exposure(
                                experimentId,
                                controlName,
                                "subject-c" + index,
                                "session-c" + index,
                                "corr-c" + index,
                                Instant.now())),
                java.util.stream.IntStream.range(0, treatmentExposures)
                    .mapToObj(
                        index ->
                            new ExperimentService.Exposure(
                                experimentId,
                                treatmentName,
                                "subject-t" + index,
                                "session-t" + index,
                                "corr-t" + index,
                                Instant.now())))
            .toList();
    List<ExperimentService.Outcome> outcomes =
        java.util.stream.Stream.concat(
                java.util.stream.IntStream.range(0, 10)
                    .mapToObj(
                        index ->
                            new ExperimentService.Outcome(
                                UUID.randomUUID(),
                                "BOOKING_COMPLETED",
                                "corr-c" + index,
                                Instant.now())),
                java.util.stream.IntStream.range(0, 20)
                    .mapToObj(
                        index ->
                            new ExperimentService.Outcome(
                                UUID.randomUUID(),
                                "BOOKING_COMPLETED",
                                "corr-t" + index,
                                Instant.now())))
            .toList();
    when(tools.invoke(
            "getExperimentExposures", new AgentToolInputs.Experiment(experimentId), EXECUTION_ID))
        .thenReturn(invocation("exposures", exposures));
    when(tools.invoke(
            "getExperimentOutcomes", new AgentToolInputs.Experiment(experimentId), EXECUTION_ID))
        .thenReturn(invocation("outcomes", outcomes));
    when(tools.invoke(
            "getExperimentPerformance", new AgentToolInputs.Experiment("experiment"), EXECUTION_ID))
        .thenReturn(
            invocation(
                "performance",
                new ExperimentPerformance(
                    "experiment",
                    "Experiment",
                    "Description",
                    "BOOKING_COMPLETED",
                    30,
                    List.of(
                        new ExperimentPerformance.Variant("control", controlExposures, 0, 0, 0, 0),
                        new ExperimentPerformance.Variant(
                            "treatment", treatmentExposures, 0, 0, 0, 0)),
                    controlExposures < 30 || treatmentExposures < 30,
                    "insufficient")));
    when(tools.invoke(
            "getExperimentExposures", new AgentToolInputs.Experiment("experiment"), EXECUTION_ID))
        .thenReturn(invocation("exposures", exposures));
    when(tools.invoke(
            "getExperimentOutcomes", new AgentToolInputs.Experiment("experiment"), EXECUTION_ID))
        .thenReturn(invocation("outcomes", outcomes));
  }

  private AgentToolInvocation invocation(String tool, Object result) {
    return new AgentToolInvocation(
        UUID.randomUUID(), tool, "evidence:" + tool, "SUCCEEDED", result);
  }
}
