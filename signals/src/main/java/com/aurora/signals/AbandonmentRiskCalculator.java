package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.util.List;

public class AbandonmentRiskCalculator extends CalculatorSupport {
  @Override
  public String name() {
    return "abandonment-risk";
  }

  @Override
  public SignalCalculation calculate(SignalDefinition definition, List<EventEnvelope> events) {
    boolean started =
        events.stream().anyMatch(event -> "BOOKING_STARTED".equals(event.eventName()));
    boolean completed =
        events.stream().anyMatch(event -> "BOOKING_COMPLETED".equals(event.eventName()));
    return new SignalCalculation(
        started && !completed ? Math.min(95, 55 + recency(definition, events) * 35) : 5,
        started && !completed
            ? "A booking was started without a completion event."
            : "No unfinished booking journey is present.",
        evidence(definition, events));
  }
}
