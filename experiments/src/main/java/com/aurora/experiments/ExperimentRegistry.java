package com.aurora.experiments;

import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class ExperimentRegistry {
  private static final Logger log = LoggerFactory.getLogger(ExperimentRegistry.class);
  private final Map<String, ExperimentDefinition> yamlDefinitions;
  private final ExperimentDefinitionRepository repository;
  private final RefreshScheduler refreshScheduler;
  private volatile Map<String, ExperimentDefinition> definitions;
  private volatile boolean databaseViewIncomplete;
  private final AtomicBoolean refreshScheduled = new AtomicBoolean();
  private static final Duration DATABASE_REFRESH_RETRY_DELAY = Duration.ofSeconds(5);

  @SuppressWarnings("unchecked")
  @Autowired
  public ExperimentRegistry(
      ResourcePatternResolver resolver,
      @Value("${aurora.experiments.location:classpath:/experiments/*.yaml}") String location,
      ExperimentDefinitionRepository repository) {
    this(resolver, location, repository, defaultRefreshScheduler());
  }

  ExperimentRegistry(
      ResourcePatternResolver resolver,
      String location,
      ExperimentDefinitionRepository repository,
      RefreshScheduler refreshScheduler) {
    this.repository = repository;
    this.refreshScheduler = refreshScheduler;
    this.yamlDefinitions = loadYaml(resolver, location);
    try {
      refresh();
    } catch (DataAccessException exception) {
      markDatabaseViewIncomplete();
      scheduleDatabaseRefresh();
      log.warn(
          "Database experiment definitions unavailable during startup; serving YAML definitions only: {}",
          exception.getMessage());
    }
  }

  public ExperimentRegistry(ResourcePatternResolver resolver, String location) {
    this.repository = null;
    this.refreshScheduler = (task, delay) -> {};
    this.yamlDefinitions = loadYaml(resolver, location);
    refresh();
  }

  public List<ExperimentDefinition> definitions() {
    return definitions.values().stream()
        .sorted(Comparator.comparing(ExperimentDefinition::id))
        .toList();
  }

  public ExperimentDefinition definition(String id) {
    ExperimentDefinition definition = definitions.get(id);
    if (definition == null) {
      throw new UnknownExperimentException(id, definitions.keySet().stream().sorted().toList());
    }
    return definition;
  }

  /**
   * Rebuilds the in-memory view explicitly. Customer-facing resolution reads only this view and
   * never queries the database per request.
   *
   * <p>YAML and database definitions have no precedence: an ID collision is rejected rather than
   * silently shadowing the committed YAML definition.
   */
  public synchronized void refresh() {
    List<ExperimentDefinition> databaseDefinitions =
        repository == null ? List.of() : repository.findAll();
    Map<String, ExperimentDefinition> refreshed = new HashMap<>(yamlDefinitions);
    databaseDefinitions.forEach(
        definition -> {
          if (refreshed.containsKey(definition.id())) {
            throw new IllegalStateException(
                "Experiment definition id '"
                    + definition.id()
                    + "' is defined in both YAML and the database; collisions are not allowed");
          }
          refreshed.put(definition.id(), definition);
        });
    definitions = Map.copyOf(refreshed);
    databaseViewIncomplete = false;
  }

  public void refreshAfterWrite(String id) {
    try {
      refresh();
    } catch (Exception exception) {
      markDatabaseViewIncomplete();
      scheduleDatabaseRefresh();
      throw new IllegalStateException(
          "Experiment definition '"
              + id
              + "' was persisted but is not yet in the serving view; background refresh has been scheduled",
          exception);
    }
  }

  public void assertCanWrite(String id) {
    if (databaseViewIncomplete) {
      throw new IllegalStateException(
          "Cannot write experiment definition while database experiment definitions are unavailable; retry after the database recovers");
    }
    if (definitions.containsKey(id)) {
      throw new IllegalStateException(
          "Experiment definition id '" + id + "' is already registered");
    }
  }

  public boolean isDatabaseViewIncomplete() {
    return databaseViewIncomplete;
  }

  @PreDestroy
  void shutdownRefreshScheduler() {
    refreshScheduler.shutdown();
  }

  // Keep recovery off customer-facing threads and throttle retries during an outage.
  private void scheduleDatabaseRefresh() {
    if (!databaseViewIncomplete || !refreshScheduled.compareAndSet(false, true)) return;
    refreshScheduler.schedule(this::runBackgroundRefresh, DATABASE_REFRESH_RETRY_DELAY);
  }

  private void runBackgroundRefresh() {
    try {
      if (!databaseViewIncomplete) return;
      refresh();
    } catch (Exception exception) {
      markDatabaseViewIncomplete();
      log.error(
          "Unable to refresh database experiment definitions; retaining the last good view and retrying later",
          exception);
    } finally {
      refreshScheduled.set(false);
      if (databaseViewIncomplete) scheduleDatabaseRefresh();
    }
  }

  private void markDatabaseViewIncomplete() {
    databaseViewIncomplete = true;
    if (definitions == null) {
      definitions = Map.copyOf(yamlDefinitions);
    }
  }

  private static RefreshScheduler defaultRefreshScheduler() {
    ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "experiment-registry-refresh");
              thread.setDaemon(true);
              return thread;
            });
    return new RefreshScheduler() {
      @Override
      public void schedule(Runnable task, Duration delay) {
        executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
      }

      @Override
      public void shutdown() {
        executor.shutdownNow();
      }
    };
  }

  @FunctionalInterface
  interface RefreshScheduler {
    void schedule(Runnable task, Duration delay);

    default void shutdown() {}
  }

  private Map<String, ExperimentDefinition> loadYaml(
      ResourcePatternResolver resolver, String location) {
    Yaml yaml = new Yaml();
    List<ExperimentDefinition> loaded = new ArrayList<>();
    try {
      Resource[] resources = resolver.getResources(location);
      if (resources.length == 0) {
        throw new IllegalStateException("No experiment definitions found at " + location);
      }
      for (Resource resource : resources) {
        try (InputStream input = resource.getInputStream()) {
          Object parsed = yaml.load(input);
          if (!(parsed instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Definition is not a YAML object");
          }
          Map<String, Object> map = new HashMap<>();
          raw.forEach((key, value) -> map.put(String.valueOf(key), value));
          loaded.add(toDefinition(map));
        } catch (Exception exception) {
          throw new IllegalStateException(
              "Unable to load experiment definition from " + resource.getDescription(), exception);
        }
      }
    } catch (Exception exception) {
      if (exception instanceof IllegalStateException illegalStateException) {
        throw illegalStateException;
      }
      throw new IllegalStateException(
          "Unable to discover experiment definitions from " + location, exception);
    }
    if (loaded.stream().map(ExperimentDefinition::id).distinct().count() != loaded.size()) {
      throw new IllegalStateException("Experiment definition IDs must be unique");
    }
    Map<String, ExperimentDefinition> byId = new HashMap<>();
    loaded.forEach(definition -> byId.put(definition.id(), definition));
    return Map.copyOf(byId);
  }

  @SuppressWarnings("unchecked")
  private ExperimentDefinition toDefinition(Map<String, Object> map) {
    Object rawVariants = map.get("variants");
    if (!(rawVariants instanceof List<?> variantList)) {
      throw new IllegalArgumentException("variants must be a list");
    }
    List<ExperimentDefinition.Variant> variants =
        variantList.stream()
            .map(
                raw -> {
                  if (!(raw instanceof Map<?, ?> variantMap)) {
                    throw new IllegalArgumentException("variant must be an object");
                  }
                  String name = String.valueOf(variantMap.get("name"));
                  Object allocation = variantMap.get("allocationPercentage");
                  if (!(allocation instanceof Number number)) {
                    throw new IllegalArgumentException(
                        "variant allocationPercentage must be numeric");
                  }
                  return new ExperimentDefinition.Variant(name, number.intValue());
                })
            .toList();
    return new ExperimentDefinition(
        requiredString(map, "id"),
        requiredString(map, "name"),
        requiredString(map, "description"),
        variants,
        requiredString(map, "primaryOutcomeEvent"),
        requiredInt(map, "minimumExposuresPerVariant"),
        ExperimentDefinition.LifecycleStatus.valueOf(
            requiredString(map, "lifecycleStatus").toUpperCase()));
  }

  private String requiredString(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (!(value instanceof String string) || string.isBlank()) {
      throw new IllegalArgumentException(key + " is required");
    }
    return string;
  }

  private int requiredInt(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (!(value instanceof Number number)) {
      throw new IllegalArgumentException(key + " must be numeric");
    }
    return number.intValue();
  }
}
