package com.aurora.ingest;

import com.aurora.common.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public EventRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public boolean exists(UUID eventId) {
    return jdbc.queryForObject(
            "select count(*) from raw_events where event_id = ?", Integer.class, eventId)
        > 0;
  }

  public boolean save(EventEnvelope event) {
    String payload;
    try {
      payload = mapper.writeValueAsString(event.payload());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize event payload", exception);
    }
    return jdbc.update(
            connection -> {
              var statement =
                  connection.prepareStatement(
                      """
                  insert into raw_events
                    (event_id,event_name,event_time,received_time,schema_version,source,session_id,
                     anonymous_id,customer_id,correlation_id,payload,analytics_consent,personalization_consent)
                  values (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)
                  on conflict (event_id) do nothing
                  """);
              statement.setObject(1, event.eventId());
              statement.setString(2, event.eventName());
              statement.setTimestamp(3, java.sql.Timestamp.from(event.eventTime()));
              statement.setTimestamp(4, java.sql.Timestamp.from(event.receivedTime()));
              statement.setString(5, event.schemaVersion());
              statement.setString(6, event.source());
              statement.setString(7, event.sessionId());
              statement.setString(8, event.anonymousId());
              if (event.customerId() == null) statement.setNull(9, Types.VARCHAR);
              else statement.setString(9, event.customerId());
              statement.setString(10, event.correlationId());
              statement.setString(11, payload);
              statement.setBoolean(12, event.consent().analytics());
              statement.setBoolean(13, event.consent().personalization());
              return statement;
            })
        > 0;
  }

  public void quarantine(UUID eventId, String reason, String originalPayload) {
    jdbc.update(
        "insert into quarantined_events(event_id,reason,raw_payload) values (?, ?, ?::jsonb)",
        eventId,
        reason,
        originalPayload);
  }

  public List<EventEnvelope> findBySession(String sessionId) {
    return jdbc.query(
        """
        select event_id,event_name,event_time,received_time,schema_version,source,session_id,
               anonymous_id,customer_id,correlation_id,payload,analytics_consent,personalization_consent
        from raw_events where session_id = ? order by event_time
        """,
        (result, row) -> toEnvelope(result),
        sessionId);
  }

  public List<EventEnvelope> findByCustomer(String customerId) {
    return jdbc.query(
        """
        select event_id,event_name,event_time,received_time,schema_version,source,session_id,
               anonymous_id,customer_id,correlation_id,payload
        from raw_events where customer_id = ? order by event_time
        """,
        (result, row) -> toEnvelope(result),
        customerId);
  }

  private EventEnvelope toEnvelope(java.sql.ResultSet result) {
    try {
      return new EventEnvelope(
          result.getObject("event_id", UUID.class),
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
          mapper.readValue(result.getString("payload"), MapPayload.TYPE));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read stored event", exception);
    }
  }

  private static final class MapPayload {
    private static final com.fasterxml.jackson.core.type.TypeReference<
            java.util.Map<String, Object>>
        TYPE = new com.fasterxml.jackson.core.type.TypeReference<>() {};
  }
}
