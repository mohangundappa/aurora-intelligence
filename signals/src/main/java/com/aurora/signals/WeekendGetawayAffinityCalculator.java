package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WeekendGetawayAffinityCalculator extends CalculatorSupport {
  @Override
  public String name() {
    return "weekend-getaway-affinity";
  }

  @Override
  public SignalCalculation calculate(SignalDefinition definition, List<EventEnvelope> events) {
    long evidence =
        events.stream()
            .filter(event -> "TRAVEL_DATES_SELECTED".equals(event.eventName()))
            .filter(this::isWeekendTrip)
            .count();
    double value = Math.min(100, evidence * 35);
    return new SignalCalculation(
        value,
        definition.explanationTemplate().replace("{evidenceCount}", String.valueOf(evidence)),
        evidence,
        Map.of("tripType", evidence > 0 ? "weekend" : "none"));
  }

  private boolean isWeekendTrip(EventEnvelope event) {
    try {
      LocalDate checkIn = LocalDate.parse(String.valueOf(event.payload().get("checkIn")));
      LocalDate checkOut = LocalDate.parse(String.valueOf(event.payload().get("checkOut")));
      return checkIn.getDayOfWeek() == DayOfWeek.FRIDAY
          || checkIn.getDayOfWeek() == DayOfWeek.SATURDAY
          || checkOut.getDayOfWeek() == DayOfWeek.SATURDAY
          || checkOut.getDayOfWeek() == DayOfWeek.SUNDAY;
    } catch (Exception exception) {
      return false;
    }
  }
}
