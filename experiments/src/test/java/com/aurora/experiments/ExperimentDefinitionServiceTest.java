package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessResourceFailureException;

class ExperimentDefinitionServiceTest {
  @Test
  void reportsPersistedDefinitionWhenPostWriteRefreshFails() {
    ExperimentDefinitionRepository repository = mock(ExperimentDefinitionRepository.class);
    when(repository.findAll())
        .thenReturn(List.of())
        .thenThrow(new DataAccessResourceFailureException("database unavailable"));
    doNothing().when(repository).save(definition());
    AtomicReference<Runnable> scheduledRefresh = new AtomicReference<>();
    ExperimentRegistry registry =
        new ExperimentRegistry(
            new PathMatchingResourcePatternResolver(),
            "classpath:/experiments/*.yaml",
            repository,
            (task, delay) -> scheduledRefresh.set(task));
    ExperimentDefinitionService service = new ExperimentDefinitionService(repository, registry);

    assertThatThrownBy(() -> service.save(definition()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("was persisted but is not yet in the serving view")
        .hasMessageContaining("background refresh has been scheduled");

    verify(repository).save(definition());
    assertThat(registry.isDatabaseViewIncomplete()).isTrue();
    assertThat(scheduledRefresh).isNotNull();
  }

  private ExperimentDefinition definition() {
    return new ExperimentDefinition(
        "database-experiment",
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
