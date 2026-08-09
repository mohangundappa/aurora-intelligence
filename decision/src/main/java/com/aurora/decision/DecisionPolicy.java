package com.aurora.decision;

import com.aurora.common.SignalSnapshot;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class DecisionPolicy {
  private final String version;
  private final String channel;
  private final Default defaultRule;
  private final List<Rule> rules;

  @SuppressWarnings("unchecked")
  public DecisionPolicy() {
    Map<String, Object> yaml;
    try (InputStream input = getClass().getResourceAsStream("/decision-policy.yaml")) {
      yaml = new Yaml().load(input);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to load decision policy", exception);
    }
    version = String.valueOf(yaml.get("version"));
    channel = String.valueOf(yaml.get("channel"));
    Map<String, Object> defaultMap = (Map<String, Object>) yaml.get("default");
    defaultRule =
        new Default(
            String.valueOf(defaultMap.get("action")),
            String.valueOf(defaultMap.get("experience")),
            (List<String>) defaultMap.get("reasonCodes"),
            String.valueOf(defaultMap.get("explanation")));
    rules =
        ((List<Map<String, Object>>) yaml.get("rules"))
            .stream()
                .map(
                    rule ->
                        new Rule(
                            String.valueOf(rule.get("id")),
                            String.valueOf(rule.get("action")),
                            String.valueOf(rule.get("experience")),
                            String.valueOf(rule.get("signal")),
                            (Map<String, String>) rule.getOrDefault("attributeEquals", Map.of()),
                            Double.parseDouble(String.valueOf(rule.get("minimumValue"))),
                            Double.parseDouble(String.valueOf(rule.get("minimumConfidence"))),
                            (List<String>) rule.get("reasonCodes"),
                            String.valueOf(rule.get("explanation"))))
                .toList();
  }

  public String version() {
    return version;
  }

  public String channel() {
    return channel;
  }

  public DecisionPolicyResult evaluate(List<SignalSnapshot> signals) {
    for (Rule rule : rules) {
      SignalSnapshot signal =
          signals.stream()
              .filter(candidate -> candidate.name().equals(rule.signal()))
              .filter(candidate -> candidate.value() >= rule.minimumValue())
              .filter(candidate -> candidate.confidence() >= rule.minimumConfidence())
              .filter(
                  candidate ->
                      rule.attributeEquals().entrySet().stream()
                          .allMatch(
                              condition ->
                                  condition
                                      .getValue()
                                      .equals(candidate.attributes().get(condition.getKey()))))
              .filter(candidate -> candidate.expiresAt().isAfter(java.time.Instant.now()))
              .findFirst()
              .orElse(null);
      if (signal != null) {
        return new DecisionPolicyResult(
            rule.id(), rule.action(), rule.experience(), rule.reasonCodes(), rule.explanation());
      }
    }
    return new DecisionPolicyResult(
        null,
        defaultRule.action(),
        defaultRule.experience(),
        defaultRule.reasonCodes(),
        defaultRule.explanation());
  }

  private record Default(
      String action, String experience, List<String> reasonCodes, String explanation) {}

  private record Rule(
      String id,
      String action,
      String experience,
      String signal,
      Map<String, String> attributeEquals,
      double minimumValue,
      double minimumConfidence,
      List<String> reasonCodes,
      String explanation) {}

  public record DecisionPolicyResult(
      String ruleId,
      String action,
      String experience,
      List<String> reasonCodes,
      String explanation) {}
}
