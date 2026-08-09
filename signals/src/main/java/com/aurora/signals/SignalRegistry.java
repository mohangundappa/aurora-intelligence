package com.aurora.signals;

import com.aurora.common.SignalDefinition;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class SignalRegistry {
  private static final List<String> SIGNALS =
      List.of(
          "destination-intent",
          "family-travel-affinity",
          "resort-affinity",
          "business-travel-affinity",
          "amenity-preference",
          "booking-intent",
          "price-sensitivity",
          "abandonment-risk",
          "journey-stage");
  private final List<SignalDefinition> definitions = new ArrayList<>();
  private final Map<String, SignalCalculator> calculators;

  public SignalRegistry() {
    Yaml yaml = new Yaml();
    for (String signal : SIGNALS) {
      try (InputStream input = getClass().getResourceAsStream("/signals/" + signal + ".yaml")) {
        Map<String, Object> map = yaml.load(input);
        definitions.add(
            new SignalDefinition(
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
                (String) map.get("owner")));
      } catch (Exception exception) {
        throw new IllegalStateException("Unable to load signal definition " + signal, exception);
      }
    }
    calculators =
        Map.ofEntries(
            Map.entry("destination-intent", new DestinationIntentCalculator()),
            Map.entry("family-travel-affinity", new FamilyTravelAffinityCalculator()),
            Map.entry(
                "resort-affinity",
                new TextAffinityCalculator(
                    "resort-affinity",
                    "resort",
                    "Resort inventory or filtering was explored.",
                    "Limited resort preference evidence.")),
            Map.entry(
                "business-travel-affinity",
                new TextAffinityCalculator(
                    "business-travel-affinity",
                    "business",
                    "Business-oriented inventory was explored.",
                    "Limited business-travel evidence.")),
            Map.entry("amenity-preference", new AmenityPreferenceCalculator()),
            Map.entry("booking-intent", new BookingIntentCalculator()),
            Map.entry("price-sensitivity", new PriceSensitivityCalculator()),
            Map.entry("abandonment-risk", new AbandonmentRiskCalculator()),
            Map.entry("journey-stage", new JourneyStageCalculator()));
  }

  public List<SignalDefinition> definitions() {
    return List.copyOf(definitions);
  }

  public SignalDefinition definition(String name) {
    return definitions.stream()
        .filter(definition -> definition.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown signal " + name));
  }

  public SignalCalculator calculator(String name) {
    SignalCalculator calculator = calculators.get(name);
    if (calculator == null)
      throw new IllegalArgumentException("No calculator registered for " + name);
    return calculator;
  }
}
