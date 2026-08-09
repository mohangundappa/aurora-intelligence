package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JourneyStageCalculator extends CalculatorSupport {
  @Override
  public String name() {
    return "journey-stage";
  }

  @Override
  public SignalCalculation calculate(SignalDefinition definition, List<EventEnvelope> events) {
    int stage =
        events.stream().anyMatch(event -> "BOOKING_COMPLETED".equals(event.eventName()))
            ? 4
            : events.stream().anyMatch(event -> "BOOKING_ABANDONED".equals(event.eventName()))
                ? 3
                : events.stream().anyMatch(event -> "BOOKING_STARTED".equals(event.eventName()))
                    ? 2
                    : events.stream()
                            .anyMatch(
                                event ->
                                    List.of("PROPERTY_VIEWED", "ROOM_VIEWED", "RATE_VIEWED")
                                        .contains(event.eventName()))
                        ? 1
                        : 0;
    String[] stages = {"Discovery", "Consideration", "Booking", "Abandoned", "Converted"};
    return new SignalCalculation(
        stage,
        "Journey stage is derived from the furthest observed funnel event: " + stages[stage] + ".",
        evidence(definition, events),
        Map.of("stage", stages[stage]));
  }
}
