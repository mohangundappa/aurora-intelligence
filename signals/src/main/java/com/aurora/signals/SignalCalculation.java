package com.aurora.signals;

import java.util.Map;

public record SignalCalculation(
    double value, String explanation, long evidenceCount, Map<String, String> attributes) {
  public SignalCalculation(double value, String explanation, long evidenceCount) {
    this(value, explanation, evidenceCount, Map.of());
  }
}
