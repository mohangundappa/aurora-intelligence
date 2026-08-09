package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.util.List;

public class PriceSensitivityCalculator extends CalculatorSupport {
  @Override
  public String name() {
    return "price-sensitivity";
  }

  @Override
  public SignalCalculation calculate(SignalDefinition definition, List<EventEnvelope> events) {
    boolean budget = contains(events, "budget");
    long evidence = evidence(definition, events);
    return new SignalCalculation(
        budget ? 80 : Math.min(60, evidence * 15),
        budget
            ? "Budget-oriented rate or filter behavior was observed."
            : "No strong price sensitivity signal.",
        evidence);
  }
}
