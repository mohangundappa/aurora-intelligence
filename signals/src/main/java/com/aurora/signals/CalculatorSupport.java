package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

abstract class CalculatorSupport implements SignalCalculator {
  protected long evidence(SignalDefinition definition, List<EventEnvelope> events) {
    return events.stream().filter(event -> definition.inputs().contains(event.eventName())).count();
  }

  protected double recency(SignalDefinition definition, List<EventEnvelope> events) {
    Instant latest =
        events.stream()
            .filter(event -> definition.inputs().contains(event.eventName()))
            .map(EventEnvelope::eventTime)
            .max(Instant::compareTo)
            .orElse(null);
    return latest == null
        ? 0
        : Math.max(0, 1 - Duration.between(latest, Instant.now()).toHours() / 720d);
  }

  protected boolean contains(List<EventEnvelope> events, String token) {
    return events.stream()
        .anyMatch(event -> String.valueOf(event.payload()).toLowerCase().contains(token));
  }
}
