package com.aurora.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurora.decision.DecisionPolicy;
import com.aurora.experiments.ExperimentRegistry;
import com.aurora.experiments.UnknownExperimentException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ExperimentPolicyValidatorTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(DecisionPolicy.class, () -> policyWithExperiment("missing-experiment"))
          .withBean(ExperimentRegistry.class, () -> registryWithoutExperiment())
          .withUserConfiguration(ExperimentPolicyValidator.class);

  @Test
  void applicationContextFailsWhenPolicyReferencesUnknownExperiment() {
    contextRunner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseInstanceOf(UnknownExperimentException.class)
              .hasRootCauseMessage(
                  "Unknown experiment 'missing-experiment'. Registered experiment ids: []")
              .hasStackTraceContaining(
                  "Decision policy references experiment 'missing-experiment' but no definition is registered.");
        });
  }

  @Test
  void applicationContextStartsWhenPolicyReferenceIsDatabaseBacked() {
    DecisionPolicy policy = policyWithExperiment("database-experiment");
    ExperimentRegistry registry = mock(ExperimentRegistry.class);
    when(registry.definition("database-experiment"))
        .thenReturn(
            new com.aurora.experiments.ExperimentDefinition(
                "database-experiment",
                "Database experiment",
                "Database experiment description",
                List.of(
                    new com.aurora.experiments.ExperimentDefinition.Variant("control", 50),
                    new com.aurora.experiments.ExperimentDefinition.Variant("treatment", 50)),
                "BOOKING_COMPLETED",
                30,
                com.aurora.experiments.ExperimentDefinition.LifecycleStatus.DEPLOYED));

    new ApplicationContextRunner()
        .withBean(DecisionPolicy.class, () -> policy)
        .withBean(ExperimentRegistry.class, () -> registry)
        .withUserConfiguration(ExperimentPolicyValidator.class)
        .run(context -> assertThat(context).hasNotFailed());
  }

  private DecisionPolicy policyWithExperiment(String experimentId) {
    DecisionPolicy policy = mock(DecisionPolicy.class);
    when(policy.experimentIds()).thenReturn(List.of(experimentId));
    return policy;
  }

  private ExperimentRegistry registryWithoutExperiment() {
    ExperimentRegistry registry = mock(ExperimentRegistry.class);
    when(registry.definition("missing-experiment"))
        .thenThrow(new UnknownExperimentException("missing-experiment", List.of()));
    return registry;
  }
}
