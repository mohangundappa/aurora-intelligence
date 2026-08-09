package com.aurora.common;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class EventCatalog {
  public enum EventType {
    PAGE_VIEWED,
    DESTINATION_SEARCHED,
    TRAVEL_DATES_SELECTED,
    TRAVEL_PARTY_SELECTED,
    FILTER_APPLIED,
    PROPERTY_VIEWED,
    ROOM_VIEWED,
    RATE_VIEWED,
    BOOKING_STARTED,
    BOOKING_ABANDONED,
    BOOKING_COMPLETED,
    CUSTOMER_IDENTIFIED,
    OFFER_PRESENTED,
    OFFER_CLICKED
  }

  private static final Map<EventType, List<String>> REQUIRED_PAYLOADS =
      Map.ofEntries(
          Map.entry(EventType.PAGE_VIEWED, List.of("path")),
          Map.entry(EventType.DESTINATION_SEARCHED, List.of("destination")),
          Map.entry(EventType.TRAVEL_DATES_SELECTED, List.of("checkIn", "checkOut")),
          Map.entry(EventType.TRAVEL_PARTY_SELECTED, List.of("adults", "children")),
          Map.entry(EventType.FILTER_APPLIED, List.of("filter", "value")),
          Map.entry(EventType.PROPERTY_VIEWED, List.of("propertyId")),
          Map.entry(EventType.ROOM_VIEWED, List.of("propertyId", "roomId")),
          Map.entry(EventType.RATE_VIEWED, List.of("propertyId", "roomId", "rate")),
          Map.entry(EventType.BOOKING_STARTED, List.of("propertyId")),
          Map.entry(EventType.BOOKING_ABANDONED, List.of("propertyId", "reason")),
          Map.entry(EventType.BOOKING_COMPLETED, List.of("propertyId", "bookingId")),
          Map.entry(EventType.CUSTOMER_IDENTIFIED, List.of("customerId")),
          Map.entry(EventType.OFFER_PRESENTED, List.of("offerId")),
          Map.entry(EventType.OFFER_CLICKED, List.of("offerId")));

  private EventCatalog() {}

  public static String validate(EventEnvelope event) {
    if (event == null) return "event is required";
    if (event.eventId() == null) return "eventId is required";
    if (event.eventName() == null || event.eventName().isBlank()) return "eventName is required";
    if (event.eventTime() == null) return "eventTime is required";
    if (event.receivedTime() == null) return "receivedTime is required";
    if (event.schemaVersion() == null || event.schemaVersion().isBlank())
      return "schemaVersion is required";
    if (event.source() == null || event.source().isBlank()) return "source is required";
    if (event.sessionId() == null || event.sessionId().isBlank()) return "sessionId is required";
    if (event.anonymousId() == null || event.anonymousId().isBlank())
      return "anonymousId is required";
    if (event.correlationId() == null || event.correlationId().isBlank())
      return "correlationId is required";
    if (event.consent() == null
        || event.consent().analytics() == null
        || event.consent().personalization() == null) {
      return "consent analytics and personalization are required";
    }
    final EventType type;
    try {
      type = EventType.valueOf(event.eventName());
    } catch (IllegalArgumentException exception) {
      return "unsupported eventName: " + event.eventName();
    }
    if (event.payload() == null) return "payload is required";
    for (String property : REQUIRED_PAYLOADS.get(type)) {
      if (!event.payload().containsKey(property)
          || event.payload().get(property) == null
          || event.payload().get(property).toString().isBlank()) {
        return "payload." + property + " is required for " + type;
      }
    }
    return null;
  }

  public static List<EventType> supportedTypes() {
    return Arrays.asList(EventType.values());
  }
}
