package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DestinationIntentCalculator extends CalculatorSupport {
  @Override
  public String name() {
    return "destination-intent";
  }

  @Override
  public SignalCalculation calculate(SignalDefinition definition, List<EventEnvelope> events) {
    long evidence = evidence(definition, events);
    double value = Math.min(100, 35 + evidence * 20 + recency(definition, events) * 25);
    String destination =
        events.stream()
            .filter(event -> "DESTINATION_SEARCHED".equals(event.eventName()))
            .reduce((first, second) -> second)
            .map(event -> String.valueOf(event.payload().get("destination")))
            .orElse("a destination");
    return new SignalCalculation(
        value,
        "A destination search for "
            + destination
            + " occurred "
            + evidence
            + " time(s); recency contributes "
            + Math.round(recency(definition, events) * 25)
            + " points.",
        evidence,
        Map.of("destination", destination));
  }
}
