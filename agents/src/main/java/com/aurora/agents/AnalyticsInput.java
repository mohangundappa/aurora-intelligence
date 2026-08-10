package com.aurora.agents;

public record AnalyticsInput(String objectiveId, String experimentId) {
  public AnalyticsInput {
    if (objectiveId == null || objectiveId.isBlank()) {
      throw new IllegalArgumentException("objectiveId is required");
    }
    if (experimentId == null || experimentId.isBlank()) {
      throw new IllegalArgumentException("experimentId is required");
    }
  }
}
