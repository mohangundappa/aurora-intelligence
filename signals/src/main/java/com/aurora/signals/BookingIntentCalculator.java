package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import com.aurora.models.ModelService;
import com.aurora.models.Prediction;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BookingIntentCalculator extends CalculatorSupport {
  private final ModelService models;

  public BookingIntentCalculator(ModelService models) {
    this.models = models;
  }

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
    Map<String, Double> features =
        Map.of(
            "propertyViewed", (double) count(events, "PROPERTY_VIEWED"),
            "roomViewed", (double) count(events, "ROOM_VIEWED"),
            "rateViewed", (double) count(events, "RATE_VIEWED"),
            "bookingStarted", (double) count(events, "BOOKING_STARTED"));
    Prediction prediction = models.predict("booking-intent", features);
    return new SignalCalculation(
        prediction.score(),
        prediction.explanation() + " Contributions: " + prediction.contributions() + ".",
        evidence,
        Map.of("modelVersion", prediction.modelVersion()));
  }

  private long count(List<EventEnvelope> events, String eventName) {
    return events.stream().filter(event -> eventName.equals(event.eventName())).count();
  }
}
