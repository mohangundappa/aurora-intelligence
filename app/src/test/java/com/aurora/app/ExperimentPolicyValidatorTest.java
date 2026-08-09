package com.aurora.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurora.decision.DecisionPolicy;
import com.aurora.experiments.ExperimentRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.server.ResponseStatusException;

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
              .hasRootCauseInstanceOf(ResponseStatusException.class)
              .hasRootCauseMessage("404 NOT_FOUND \"Unknown experiment missing-experiment\"");
        });
  }

  private DecisionPolicy policyWithExperiment(String experimentId) {
    DecisionPolicy policy = mock(DecisionPolicy.class);
    when(policy.experimentIds()).thenReturn(List.of(experimentId));
    return policy;
  }

  private ExperimentRegistry registryWithoutExperiment() {
    ExperimentRegistry registry = mock(ExperimentRegistry.class);
    when(registry.definition("missing-experiment"))
        .thenThrow(
            new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Unknown experiment missing-experiment"));
    return registry;
  }
}
