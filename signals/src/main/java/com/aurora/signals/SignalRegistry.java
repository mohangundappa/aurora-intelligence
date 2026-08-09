package com.aurora.signals;

import com.aurora.common.SignalDefinition;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class SignalRegistry {
  private final List<SignalDefinition> definitions;
  private final Map<String, SignalCalculator> calculators;

  @SuppressWarnings("unchecked")
  public SignalRegistry(
      ResourcePatternResolver resolver,
      List<SignalCalculator> calculators,
      @Value("${aurora.signals.location:classpath:/signals/*.yaml}") String location) {
    this.calculators =
        calculators.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    SignalCalculator::name, calculator -> calculator));
    Yaml yaml = new Yaml();
    List<SignalDefinition> loaded = new ArrayList<>();
    try {
      Resource[] resources = resolver.getResources(location);
      for (Resource resource : resources) {
        try (InputStream input = resource.getInputStream()) {
          loaded.add(toDefinition(yaml.load(input)));
        }
      }
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Unable to discover signal definitions from " + location, exception);
    }
    definitions = loaded.stream().sorted(Comparator.comparing(SignalDefinition::name)).toList();
  }

  public List<SignalDefinition> definitions() {
    return definitions;
  }

  public SignalDefinition definition(String name) {
    return definitions.stream()
        .filter(definition -> definition.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown signal " + name));
  }

  public SignalCalculator calculator(String name) {
    SignalCalculator calculator = calculators.get(name);
    if (calculator == null) {
      throw new IllegalArgumentException("No calculator registered for " + name);
    }
    return calculator;
  }

  private SignalDefinition toDefinition(Map<String, Object> map) {
    return new SignalDefinition(
        (String) map.get("name"),
        (String) map.get("version"),
        (List<String>) map.get("inputs"),
        SignalDefinition.CalculationType.valueOf(
            ((String) map.get("calculationType")).toUpperCase()),
        (String) map.get("tier"),
        (String) map.get("lookback"),
        (String) map.get("outputRange"),
        (String) map.get("confidence"),
        (String) map.get("freshness"),
        (String) map.get("expiry"),
        (Boolean) map.get("consentRequired"),
        (String) map.get("explanationTemplate"),
        SignalDefinition.LifecycleStatus.valueOf(
            ((String) map.get("lifecycleStatus")).toUpperCase()),
        (String) map.get("owner"));
  }
}
