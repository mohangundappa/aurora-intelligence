package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class ExperimentRegistryTest {
  @Test
  void discoversCommittedYamlDefinition() {
    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(), "classpath:/experiments/*.yaml");

    assertThat(registry.definitions()).hasSize(1);
    assertThat(registry.definition("destination-experience-v1").variants())
        .extracting(ExperimentDefinition.Variant::name)
        .containsExactly("control", "treatment");
  }

  @Test
  void missingDefinitionLocationFailsLoudly() {
    assertThatThrownBy(
            () ->
                new ExperimentRegistry(
                    new PathMatchingResourcePatternResolver(), "classpath:/missing/*.yaml"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to discover experiment definitions");
  }
}
