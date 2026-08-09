package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import com.aurora.common.SignalSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SignalEngine {
  private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final SignalRegistry registry;

  public SignalEngine(JdbcTemplate jdbc, ObjectMapper mapper, SignalRegistry registry) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.registry = registry;
  }

  public List<SignalSnapshot> calculateAll(String sessionId) {
    List<EventEnvelope> events =
        jdbc.query(
            """
            select event_id,event_name,event_time,received_time,schema_version,source,session_id,
                   anonymous_id,customer_id,correlation_id,payload,analytics_consent,personalization_consent
            from raw_events where session_id = ? order by event_time
            """,
            (result, row) -> toEvent(result),
            sessionId);
    if (events.isEmpty()) return List.of();
    List<SignalSnapshot> snapshots = new ArrayList<>();
    for (SignalDefinition definition : registry.definitions()) {
      SignalSnapshot snapshot = calculate(definition, events);
      if (snapshot != null) {
        persist(snapshot);
        snapshots.add(snapshot);
      }
    }
    return snapshots;
  }

  public SignalDefinition definition(String name) {
    return registry.definition(name);
  }

  public List<SignalDefinition> registryDefinitions() {
    return registry.definitions();
  }

  private SignalSnapshot calculate(SignalDefinition definition, List<EventEnvelope> events) {
    EventEnvelope latest = events.get(events.size() - 1);
    if (definition.consentRequired()
        && events.stream().noneMatch(event -> event.consent().personalization())) return null;
    Instant now = Instant.now();
    Instant mostRecent =
        events.stream()
            .filter(event -> definition.inputs().contains(event.eventName()))
            .map(EventEnvelope::eventTime)
            .max(Instant::compareTo)
            .orElse(null);
    if (mostRecent == null && !"journey-stage".equals(definition.name())) return null;

    long matching =
        events.stream().filter(event -> definition.inputs().contains(event.eventName())).count();
    double recency =
        mostRecent == null
            ? 0
            : Math.max(0, 1 - Duration.between(mostRecent, now).toHours() / 720d);
    double value;
    String explanation;
    switch (definition.name()) {
      case "destination-intent" -> {
        value = Math.min(100, 35 + matching * 20 + recency * 25);
        String destination =
            events.stream()
                .filter(event -> "DESTINATION_SEARCHED".equals(event.eventName()))
                .reduce((first, second) -> second)
                .map(event -> String.valueOf(event.payload().get("destination")))
                .orElse("a destination");
        explanation =
            "A destination search for "
                + destination
                + " occurred "
                + matching
                + " time(s); recency contributes "
                + Math.round(recency * 25)
                + " points.";
      }
      case "family-travel-affinity" -> {
        long family =
            events.stream()
                .filter(event -> "TRAVEL_PARTY_SELECTED".equals(event.eventName()))
                .filter(
                    event ->
                        Integer.parseInt(
                                String.valueOf(event.payload().getOrDefault("children", 0)))
                            > 0)
                .count();
        value = Math.min(100, family * 55 + recency * 30);
        explanation =
            family > 0
                ? "A travel party with children was selected."
                : "No family-party evidence is present.";
      }
      case "resort-affinity" -> {
        boolean resort =
            events.stream()
                .anyMatch(
                    event -> String.valueOf(event.payload()).toLowerCase().contains("resort"));
        value = resort ? 75 + recency * 20 : 15;
        explanation =
            resort
                ? "Resort inventory or filtering was explored."
                : "Limited resort preference evidence.";
      }
      case "business-travel-affinity" -> {
        boolean business =
            events.stream()
                .anyMatch(
                    event -> String.valueOf(event.payload()).toLowerCase().contains("business"));
        value = business ? 78 + recency * 15 : 10;
        explanation =
            business
                ? "Business-oriented inventory was explored."
                : "Limited business-travel evidence.";
      }
      case "amenity-preference" -> {
        value = Math.min(100, matching * 30 + recency * 30);
        explanation = "Amenity and filter interactions were aggregated over the session.";
      }
      case "booking-intent" -> {
        long bookingSteps =
            events.stream()
                .filter(
                    event ->
                        List.of("PROPERTY_VIEWED", "ROOM_VIEWED", "RATE_VIEWED", "BOOKING_STARTED")
                            .contains(event.eventName()))
                .count();
        value = Math.min(100, bookingSteps * 18 + recency * 28);
        explanation =
            "The explainable baseline score combines booking funnel steps and event recency.";
      }
      case "price-sensitivity" -> {
        boolean lowRate =
            events.stream()
                .anyMatch(
                    event -> String.valueOf(event.payload()).toLowerCase().contains("budget"));
        value = lowRate ? 80 : Math.min(60, matching * 15);
        explanation =
            lowRate
                ? "Budget-oriented rate or filter behavior was observed."
                : "No strong price sensitivity signal.";
      }
      case "abandonment-risk" -> {
        boolean started =
            events.stream().anyMatch(event -> "BOOKING_STARTED".equals(event.eventName()));
        boolean completed =
            events.stream().anyMatch(event -> "BOOKING_COMPLETED".equals(event.eventName()));
        value = started && !completed ? Math.min(95, 55 + recency * 35) : 5;
        explanation =
            started && !completed
                ? "A booking was started without a completion event."
                : "No unfinished booking journey is present.";
      }
      case "journey-stage" -> {
        value =
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
        explanation =
            "Journey stage is derived from the furthest observed funnel event: "
                + stages[(int) value]
                + ".";
      }
      default -> throw new IllegalStateException("Unsupported signal " + definition.name());
    }
    double confidence = Math.min(0.99, 0.45 + Math.min(0.4, matching * 0.1) + recency * 0.1);
    Duration freshness = parseDuration(definition.freshness());
    Duration expiry = parseDuration(definition.expiry());
    Instant expiresAt = now.plus(expiry);
    return new SignalSnapshot(
        definition.name(),
        Math.round(value * 100) / 100d,
        Math.round(confidence * 100) / 100d,
        now,
        expiresAt,
        explanation,
        "YAML definition "
            + definition.version()
            + " / "
            + definition.calculationType()
            + " over "
            + matching
            + " matching event(s); freshness window "
            + freshness,
        latest.sessionId(),
        latest.customerId(),
        latest.correlationId());
  }

  private void persist(SignalSnapshot snapshot) {
    jdbc.update(
        """
        insert into derived_signals(name,value,confidence,computed_at,expires_at,explanation,provenance,
                                     session_id,customer_id,correlation_id)
        values (?,?,?,?,?,?,?,?,?,?)
        """,
        snapshot.name(),
        snapshot.value(),
        snapshot.confidence(),
        java.sql.Timestamp.from(snapshot.computedAt()),
        java.sql.Timestamp.from(snapshot.expiresAt()),
        snapshot.explanation(),
        snapshot.provenance(),
        snapshot.sessionId(),
        snapshot.customerId(),
        snapshot.correlationId());
  }

  private EventEnvelope toEvent(ResultSet result) {
    try {
      return new EventEnvelope(
          result.getObject("event_id", java.util.UUID.class),
          result.getString("event_name"),
          result.getTimestamp("event_time").toInstant(),
          result.getTimestamp("received_time").toInstant(),
          result.getString("schema_version"),
          result.getString("source"),
          result.getString("session_id"),
          result.getString("anonymous_id"),
          result.getString("customer_id"),
          result.getString("correlation_id"),
          new EventEnvelope.Consent(
              result.getBoolean("analytics_consent"), result.getBoolean("personalization_consent")),
          mapper.readValue(result.getString("payload"), PAYLOAD_TYPE));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read raw event", exception);
    }
  }

  private Duration parseDuration(String value) {
    if (value == null || value.isBlank()) return Duration.ofHours(24);
    String normalized = value.trim().toLowerCase();
    long amount = Long.parseLong(normalized.substring(0, normalized.length() - 1));
    return switch (normalized.charAt(normalized.length() - 1)) {
      case 'm' -> Duration.ofMinutes(amount);
      case 'h' -> Duration.ofHours(amount);
      case 'd' -> Duration.ofDays(amount);
      default -> Duration.ofHours(24);
    };
  }
}
