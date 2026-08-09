package com.aurora.experiments;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class ExperimentRegistry {
  private final List<ExperimentDefinition> definitions;

  @SuppressWarnings("unchecked")
  public ExperimentRegistry(
      ResourcePatternResolver resolver,
      @Value("${aurora.experiments.location:classpath:/experiments/*.yaml}") String location) {
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
    definitions = loaded.stream().sorted(Comparator.comparing(ExperimentDefinition::id)).toList();
  }

  public List<ExperimentDefinition> definitions() {
    return definitions;
  }

  public ExperimentDefinition definition(String id) {
    return definitions.stream()
        .filter(definition -> definition.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown experiment " + id));
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
