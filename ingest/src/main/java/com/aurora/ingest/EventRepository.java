package com.aurora.ingest;

import com.aurora.common.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.util.List;
import java.util.Map;
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

  public List<SessionSummary> recentSessions() {
    return jdbc.query(
        """
        select r.session_id, max(r.event_time) last_activity,
               max(r.payload->>'destination') filter (where r.event_name = 'DESTINATION_SEARCHED') destination,
               coalesce(max(r.customer_id), max(i.customer_id)) customer_id, max(r.anonymous_id) anonymous_id
        from raw_events r left join identity_links i on i.anonymous_id = r.anonymous_id
        group by r.session_id order by last_activity desc limit 50
        """,
        (result, row) ->
            new SessionSummary(
                result.getString("session_id"),
                result.getString("destination"),
                result.getString("customer_id"),
                result.getString("anonymous_id"),
                result.getTimestamp("last_activity").toInstant()));
  }

  public record SessionSummary(
      String sessionId,
      String destination,
      String customerId,
      String anonymousId,
      java.time.Instant lastActivity) {}

  public Map<String, Object> qualityStats() {
    Integer total = jdbc.queryForObject("select count(*) from raw_events", Integer.class);
    Integer quarantined =
        jdbc.queryForObject("select count(*) from quarantined_events", Integer.class);
    Map<String, Object> quality = new java.util.LinkedHashMap<>();
    quality.putAll(
        Map.of(
            "ingestCount", total == null ? 0 : total,
            "quarantineCount", quarantined == null ? 0 : quarantined,
            "quarantineRate",
                total == null || total == 0
                    ? 0
                    : (double) (quarantined == null ? 0 : quarantined) / total));
    quality.put("quarantineReasons", quarantineReasons());
    quality.put(
        "decisionLatencyMs",
        jdbc.queryForObject(
            """
        select coalesce(avg(greatest(0, extract(epoch from (d.created_at - r.received_time)) * 1000)), 0)
        from decisions d join raw_events r on r.correlation_id=d.correlation_id
        """,
            Double.class));
    quality.put(
        "consumerLagMs",
        jdbc.queryForObject(
            """
        select coalesce(greatest(0, extract(epoch from (max(r.received_time) - max(s.computed_at))) * 1000), 0)
        from raw_events r cross join derived_signals s
        """,
            Double.class));
    quality.put(
        "signalFreshnessDistribution",
        jdbc.query(
            """
        select name, coalesce(avg(extract(epoch from (expires_at - computed_at)) / 60), 0) freshness_minutes
        from derived_signals group by name order by name
        """,
            (result, row) ->
                Map.of(
                    "signal", result.getString("name"),
                    "freshnessMinutes", result.getDouble("freshness_minutes"))));
    return quality;
  }

  private Map<String, Integer> quarantineReasons() {
    return jdbc
        .query(
            "select reason, count(*) total from quarantined_events group by reason order by total desc",
            (result, row) -> Map.entry(result.getString("reason"), result.getInt("total")))
        .stream()
        .collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                Integer::sum,
                java.util.LinkedHashMap::new));
  }

  public Funnel funnel(String sessionId) {
    String filter = sessionId == null ? "" : " where session_id = ?";
    List<Object> args = sessionId == null ? List.of() : List.of(sessionId);
    Map<String, Long> eventCounts =
        jdbc
            .queryForList(
                "select event_name, count(distinct session_id) total from raw_events"
                    + filter
                    + " group by event_name",
                args.toArray())
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    row -> (String) row.get("event_name"),
                    row -> ((Number) row.get("total")).longValue(),
                    Long::sum,
                    java.util.LinkedHashMap::new));
    List<String> stages =
        List.of(
            "DESTINATION_SEARCHED",
            "PROPERTY_VIEWED",
            "ROOM_VIEWED",
            "RATE_VIEWED",
            "BOOKING_STARTED",
            "BOOKING_COMPLETED");
    List<FunnelStage> result = new java.util.ArrayList<>();
    long previous = 0;
    for (String stage : stages) {
      long count = eventCounts.getOrDefault(stage, 0L);
      result.add(new FunnelStage(stage, count, previous == 0 ? 0 : previous - count));
      previous = count;
    }
    return new Funnel(sessionId, result);
  }

  public record Funnel(String sessionId, List<FunnelStage> stages) {}

  public record FunnelStage(String stage, long sessions, long dropOff) {}

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
