package com.aurora.experiments;

import java.util.List;
import java.util.Map;

public final class ExperimentDefinitions {
  private static final List<String> DEFAULT_VARIANTS = List.of("control", "treatment");
  private static final Map<String, List<String>> DEFINITIONS =
      Map.of("destination-experience-v1", DEFAULT_VARIANTS);

  private ExperimentDefinitions() {}

  public static List<String> variants(String experimentId) {
    return DEFINITIONS.getOrDefault(experimentId, DEFAULT_VARIANTS);
  }
}
