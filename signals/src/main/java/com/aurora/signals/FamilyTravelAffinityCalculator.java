package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.util.List;

public class FamilyTravelAffinityCalculator extends CalculatorSupport {
  @Override
  public String name() {
    return "family-travel-affinity";
  }

  @Override
  public SignalCalculation calculate(SignalDefinition definition, List<EventEnvelope> events) {
    long family =
        events.stream()
            .filter(event -> "TRAVEL_PARTY_SELECTED".equals(event.eventName()))
            .filter(
                event ->
                    Integer.parseInt(String.valueOf(event.payload().getOrDefault("children", 0)))
                        > 0)
            .count();
    return new SignalCalculation(
        Math.min(100, family * 55 + recency(definition, events) * 30),
        family > 0
            ? "A travel party with children was selected."
            : "No family-party evidence is present.",
        family);
  }
}
