package com.aurora.common;

import java.util.List;

public record SignalDefinition(
    String name,
    String version,
    List<String> inputs,
    CalculationType calculationType,
    String tier,
    String lookback,
    String outputRange,
    String confidence,
    String freshness,
    String expiry,
    boolean consentRequired,
    String explanationTemplate,
    LifecycleStatus lifecycleStatus,
    String owner) {
  public enum CalculationType {
    RULE,
    AGGREGATION,
    MODEL
  }

  public enum LifecycleStatus {
    DRAFT,
    TESTED,
    APPROVED,
    DEPLOYED,
    RETIRED
  }
}
