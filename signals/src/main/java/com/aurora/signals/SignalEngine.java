package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import com.aurora.common.SignalSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
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
    return calculateAll(sessionId, true);
  }

  public List<SignalSnapshot> calculateAllReadOnly(String sessionId) {
    return calculateAll(sessionId, false);
  }

  private List<SignalSnapshot> calculateAll(String sessionId, boolean persist) {
    List<EventEnvelope> events =
        jdbc.query(
            """
            select event_id,event_name,event_time,received_time,schema_version,source,session_id,
                   anonymous_id,customer_id,correlation_id,payload,analytics_consent,personalization_consent
            from raw_events where session_id = ? order by event_time
            """,
            (result, row) -> toEvent(result),
            sessionId);
    return registry.definitions().stream()
        .map(
            definition ->
                java.util.Map.entry(definition, evidenceForDefinition(definition, events)))
        .filter(entry -> eligible(entry.getKey(), entry.getValue()))
        .map(entry -> calculate(entry.getKey(), entry.getValue(), persist))
        .toList();
  }

  public SignalDefinition definition(String name) {
    return registry.definition(name);
  }

  public List<SignalDefinition> registryDefinitions() {
    return registry.definitions();
  }

  private boolean eligible(SignalDefinition definition, List<EventEnvelope> events) {
    return !events.isEmpty()
        && (!definition.consentRequired()
            || events.stream().anyMatch(event -> event.consent().personalization()));
  }

  public List<EventEnvelope> evidenceForDefinition(
      SignalDefinition definition, List<EventEnvelope> events) {
    if (!definition.consentRequired()) return events;
    return events.stream().filter(event -> event.consent().personalization()).toList();
  }

  private SignalSnapshot calculate(
      SignalDefinition definition, List<EventEnvelope> events, boolean persist) {
    SignalCalculation calculation =
        registry.calculator(definition.name()).calculate(definition, events);
    Instant now = Instant.now();
    Duration expiry = parseDuration(definition.expiry());
    EventEnvelope latest = events.get(events.size() - 1);
    SignalSnapshot snapshot =
        new SignalSnapshot(
            definition.name(),
            Math.round(calculation.value() * 100) / 100d,
            confidence(calculation.evidenceCount()),
            now,
            now.plus(expiry),
            calculation.explanation(),
            "YAML definition "
                + definition.version()
                + " / "
                + definition.calculationType()
                + " over "
                + calculation.evidenceCount()
                + " matching event(s); freshness window "
                + definition.freshness(),
            calculation.attributes(),
            latest.sessionId(),
            latest.customerId(),
            latest.correlationId());
    if (persist) persist(snapshot);
    return snapshot;
  }

  private double confidence(long evidence) {
    return Math.min(0.99, 0.45 + Math.min(0.4, evidence * 0.1) + 0.1);
  }

  private void persist(SignalSnapshot snapshot) {
    jdbc.update(
        """
        insert into derived_signals(name,value,confidence,computed_at,expires_at,explanation,provenance,
                                     attributes,session_id,customer_id,correlation_id)
        values (?,?,?,?,?,?,?,?::jsonb,?,?,?)
        """,
        snapshot.name(),
        snapshot.value(),
        snapshot.confidence(),
        java.sql.Timestamp.from(snapshot.computedAt()),
        java.sql.Timestamp.from(snapshot.expiresAt()),
        snapshot.explanation(),
        snapshot.provenance(),
        attributes(snapshot),
        snapshot.sessionId(),
        snapshot.customerId(),
        snapshot.correlationId());
  }

  private String attributes(SignalSnapshot snapshot) {
    try {
      return mapper.writeValueAsString(snapshot.attributes());
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialize signal attributes", exception);
    }
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
