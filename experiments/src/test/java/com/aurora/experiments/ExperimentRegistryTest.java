package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
  void staleDatabaseDefinitionCanBeRecreatedAfterExternalReset() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    ExperimentDefinition databaseDefinition = definition("database-experiment");
    when(repository.findAll()).thenReturn(List.of(databaseDefinition), List.of());
    when(repository.existsById("database-experiment")).thenReturn(false);

    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(), "classpath:/experiments/*.yaml", repository);

    registry.assertCanWrite("database-experiment");

    assertThat(registry.definitions())
        .extracting(ExperimentDefinition::id)
        .doesNotContain("database-experiment");
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
    AtomicReference<Runnable> scheduledRefresh = new AtomicReference<>();
    when(repository.findAll())
        .thenThrow(new DataAccessResourceFailureException("database unavailable"));

    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(),
            "classpath:/experiments/*.yaml",
            repository,
            (task, delay) -> scheduledRefresh.set(task));

    assertThat(registry.definitions())
        .extracting(ExperimentDefinition::id)
        .containsExactly("destination-experience-v1");
    assertThat(registry.isDatabaseViewIncomplete()).isTrue();
    verify(repository).findAll();
    assertThatThrownBy(() -> registry.assertCanWrite("new-experiment"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("database experiment definitions are unavailable");
  }

  @Test
  void lookupDuringOutageUsesYamlViewWithoutRetryingDatabase() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    AtomicReference<Runnable> scheduledRefresh = new AtomicReference<>();
    when(repository.findAll())
        .thenThrow(new DataAccessResourceFailureException("database unavailable"))
        .thenReturn(List.of(definition("database-experiment")));

    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(),
            "classpath:/experiments/*.yaml",
            repository,
            (task, delay) -> scheduledRefresh.set(task));

    assertThat(registry.isDatabaseViewIncomplete()).isTrue();
    assertThat(registry.definition("destination-experience-v1")).isNotNull();
    verify(repository).findAll();
    assertThat(scheduledRefresh).isNotNull();
  }

  @Test
  void backgroundRefreshRecoversTheIncompleteDatabaseView() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    AtomicReference<Runnable> scheduledRefresh = new AtomicReference<>();
    when(repository.findAll())
        .thenThrow(new DataAccessResourceFailureException("database unavailable"))
        .thenReturn(List.of(definition("database-experiment")));

    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(),
            "classpath:/experiments/*.yaml",
            repository,
            (task, delay) -> scheduledRefresh.set(task));

    scheduledRefresh.get().run();

    assertThat(registry.isDatabaseViewIncomplete()).isFalse();
    assertThat(registry.definition("database-experiment"))
        .isEqualTo(definition("database-experiment"));
  }

  @Test
  void invalidDatabaseDefinitionStillFailsLoudlyAtStartup() {
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

  @Test
  void invalidDatabaseDefinitionDuringBackgroundRefreshRetainsServingView() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    AtomicReference<Runnable> scheduledRefresh = new AtomicReference<>();
    when(repository.findAll())
        .thenThrow(new DataAccessResourceFailureException("database unavailable"))
        .thenThrow(new IllegalArgumentException("allocations must sum to 100"));

    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(),
            "classpath:/experiments/*.yaml",
            repository,
            (task, delay) -> scheduledRefresh.set(task));

    scheduledRefresh.get().run();

    assertThat(registry.definition("destination-experience-v1")).isNotNull();
    assertThat(registry.isDatabaseViewIncomplete()).isTrue();
  }

  @Test
  void collidingDatabaseDefinitionDuringBackgroundRefreshRetainsServingView() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    AtomicReference<Runnable> scheduledRefresh = new AtomicReference<>();
    when(repository.findAll())
        .thenThrow(new DataAccessResourceFailureException("database unavailable"))
        .thenReturn(List.of(definition("destination-experience-v1")));

    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(),
            "classpath:/experiments/*.yaml",
            repository,
            (task, delay) -> scheduledRefresh.set(task));

    scheduledRefresh.get().run();

    assertThat(registry.definition("destination-experience-v1")).isNotNull();
    assertThat(registry.isDatabaseViewIncomplete()).isTrue();
  }

  @Test
  void shutsDownInjectedRefreshSchedulerWithTheRegistry() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    ExperimentRegistry.RefreshScheduler scheduler = mock(ExperimentRegistry.RefreshScheduler.class);
    when(repository.findAll()).thenReturn(List.of());

    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(),
            "classpath:/experiments/*.yaml",
            repository,
            scheduler);

    registry.shutdownRefreshScheduler();

    verify(scheduler).shutdown();
  }

  @Test
  void rejectedRecoverySchedulingClearsLatchAndPreservesWriteDiagnostics() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    AtomicInteger attempts = new AtomicInteger();
    when(repository.findAll())
        .thenThrow(new DataAccessResourceFailureException("database unavailable"));

    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(),
            "classpath:/experiments/*.yaml",
            repository,
            (task, delay) -> {
              attempts.incrementAndGet();
              throw new java.util.concurrent.RejectedExecutionException("scheduler stopped");
            });

    assertThat(attempts).hasValue(1);
    assertThatThrownBy(() -> registry.refreshAfterWrite("new-experiment"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("persisted but is not yet in the serving view");
    assertThat(attempts).hasValue(2);
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
