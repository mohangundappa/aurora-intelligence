package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessResourceFailureException;

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

  @Test
  void resolvesDatabaseDefinitionInTheRefreshableView() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    ExperimentDefinition databaseDefinition = definition("database-experiment");
    when(repository.findAll()).thenReturn(List.of(databaseDefinition));

    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(), "classpath:/experiments/*.yaml", repository);

    assertThat(registry.definition("database-experiment")).isEqualTo(databaseDefinition);
  }

  @Test
  void yamlAndDatabaseIdCollisionFailsLoudly() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    when(repository.findAll()).thenReturn(List.of(definition("destination-experience-v1")));

    assertThatThrownBy(
            () ->
                new ExperimentRegistry(
                    new PathMatchingResourcePatternResolver(),
                    "classpath:/experiments/*.yaml",
                    repository))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("defined in both YAML and the database");
  }

  @Test
  void databaseUnavailableAtStartupFallsBackToYamlWithWarningPath() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    when(repository.findAll())
        .thenThrow(new DataAccessResourceFailureException("database unavailable"));

    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(), "classpath:/experiments/*.yaml", repository);

    assertThat(registry.definitions())
        .extracting(ExperimentDefinition::id)
        .containsExactly("destination-experience-v1");
  }

  @Test
  void invalidDatabaseDefinitionStillFailsLoudly() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    when(repository.findAll())
        .thenThrow(new IllegalArgumentException("allocations must sum to 100"));

    assertThatThrownBy(
            () ->
                new ExperimentRegistry(
                    new PathMatchingResourcePatternResolver(),
                    "classpath:/experiments/*.yaml",
                    repository))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("allocations");
  }

  private ExperimentDefinition definition(String id) {
    return new ExperimentDefinition(
        id,
        "Database experiment",
        "Database experiment description",
        List.of(
            new ExperimentDefinition.Variant("control", 50),
            new ExperimentDefinition.Variant("treatment", 50)),
        "BOOKING_COMPLETED",
        30,
        ExperimentDefinition.LifecycleStatus.DEPLOYED);
  }
}
