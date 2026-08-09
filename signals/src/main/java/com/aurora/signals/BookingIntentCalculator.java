package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.util.List;

public class BookingIntentCalculator extends CalculatorSupport {
  @Override
  public String name() {
    return "booking-intent";
  }

  @Override
  public SignalCalculation calculate(SignalDefinition definition, List<EventEnvelope> events) {
    long evidence =
        events.stream()
            .filter(
                event ->
                    List.of("PROPERTY_VIEWED", "ROOM_VIEWED", "RATE_VIEWED", "BOOKING_STARTED")
                        .contains(event.eventName()))
            .count();
    return new SignalCalculation(
        Math.min(100, evidence * 18 + recency(definition, events) * 28),
        "The explainable baseline score combines booking funnel steps and event recency.",
        evidence);
  }
}
