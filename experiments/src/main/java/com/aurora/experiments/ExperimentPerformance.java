package com.aurora.experiments;

import java.util.List;

public record ExperimentPerformance(
    String experimentId,
    Variant control,
    Variant treatment,
    List<Variant> variants,
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
