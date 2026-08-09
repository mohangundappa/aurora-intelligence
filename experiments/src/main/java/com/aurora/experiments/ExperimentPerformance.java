package com.aurora.experiments;

public record ExperimentPerformance(
    String experimentId,
    Variant control,
    Variant treatment,
    boolean insufficientSample,
    String warning) {
  public record Variant(
      String name,
      int exposed,
      int clicks,
      int bookingStarts,
      int completions,
      double conversionRate) {}
}
