package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.util.List;

public class AmenityPreferenceCalculator extends CalculatorSupport {
  @Override
  public String name() {
    return "amenity-preference";
  }

  @Override
  public SignalCalculation calculate(SignalDefinition definition, List<EventEnvelope> events) {
    long evidence = evidence(definition, events);
    return new SignalCalculation(
        Math.min(100, evidence * 30 + recency(definition, events) * 30),
        "Amenity and filter interactions were aggregated over the session.",
        evidence);
  }
}
