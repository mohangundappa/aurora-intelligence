package com.aurora.cdp;

import com.aurora.common.CdpProfile;
import com.aurora.common.EventEnvelope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SimulatedCdpAdapter implements CdpAdapter {
  private static final TypeReference<Map<String, String>> ATTRIBUTES = new TypeReference<>() {};
  private static final TypeReference<Set<String>> AUDIENCES = new TypeReference<>() {};
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public SimulatedCdpAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public void accept(EventEnvelope event) {
    String attributes = attributes(event);
    jdbc.update(
        """
        insert into cdp_profiles
          (anonymous_id,customer_id,loyalty_tier,loyalty_points,analytics_consent,
           personalization_consent,attributes,updated_at)
        values (?,?,?,?,?,?,?::jsonb,?)
        on conflict (anonymous_id) do update set
          analytics_consent = excluded.analytics_consent,
          personalization_consent = excluded.personalization_consent,
          attributes = cdp_profiles.attributes || excluded.attributes,
          updated_at = excluded.updated_at
        """,
        event.anonymousId(),
        event.customerId(),
        event.customerId() == null ? "Guest" : "Aurora Circle",
        event.customerId() == null ? 0 : 1200,
        event.consent().analytics(),
        event.consent().personalization(),
        attributes,
        Timestamp.from(Instant.now()));
  }

  @Override
  public void linkIdentity(EventEnvelope event) {
    String customerId = String.valueOf(event.payload().get("customerId"));
    Integer absorbed =
        jdbc.queryForObject(
            "select count(*) from raw_events where anonymous_id = ?",
            Integer.class,
            event.anonymousId());
    jdbc.update(
        """
        insert into identity_links
          (anonymous_id,customer_id,event_id,linked_at,link_method,correlation_id,absorbed_event_count)
        values (?,?,?,?,?,?,?)
        on conflict (event_id) do nothing
        """,
        event.anonymousId(),
        customerId,
        event.eventId(),
        Timestamp.from(event.eventTime()),
        "CUSTOMER_IDENTIFIED",
        event.correlationId(),
        absorbed == null ? 0 : absorbed);
    jdbc.update(
        "update raw_events set customer_id = ? where anonymous_id = ? and customer_id is null",
        customerId,
        event.anonymousId());
    jdbc.update(
        """
        insert into cdp_profiles
          (anonymous_id,customer_id,loyalty_tier,loyalty_points,analytics_consent,
           personalization_consent,attributes,updated_at)
        values (?,?,?,?,?,?, '{}'::jsonb,?)
        on conflict (anonymous_id) do update set
          customer_id = excluded.customer_id,
          loyalty_tier = excluded.loyalty_tier,
          loyalty_points = excluded.loyalty_points,
          updated_at = excluded.updated_at
        """,
        event.anonymousId(),
        customerId,
        "Aurora Circle",
        1200,
        event.consent().analytics(),
        event.consent().personalization(),
        Timestamp.from(Instant.now()));
  }

  @Override
  public CdpProfile profile(String anonymousId) {
    List<CdpProfile> profiles =
        jdbc.query(
            """
            select anonymous_id,customer_id,loyalty_tier,loyalty_points,analytics_consent,
                   personalization_consent,attributes,audiences
            from cdp_profiles where anonymous_id = ?
            """,
            (result, row) -> {
              try {
                String customerId = result.getString("customer_id");
                return new CdpProfile(
                    result.getString("anonymous_id"),
                    customerId,
                    new CdpProfile.Identity(anonymousId, customerId, customerId != null),
                    new CdpProfile.Loyalty(
                        result.getString("loyalty_tier"),
                        result.getInt("loyalty_points"),
                        customerId != null),
                    new CdpProfile.ConsentState(
                        result.getBoolean("analytics_consent"),
                        result.getBoolean("personalization_consent")),
                    mapper.readValue(result.getString("attributes"), ATTRIBUTES),
                    mapper.readValue(result.getString("audiences"), AUDIENCES),
                    identityTimeline(anonymousId));
              } catch (Exception exception) {
                throw new IllegalStateException("Unable to read CDP profile", exception);
              }
            },
            anonymousId);
    if (!profiles.isEmpty()) return profiles.get(0);
    return new CdpProfile(
        anonymousId,
        null,
        new CdpProfile.Identity(anonymousId, null, false),
        new CdpProfile.Loyalty("Guest", 0, false),
        new CdpProfile.ConsentState(false, false),
        Map.of(),
        Set.of(),
        List.of());
  }

  private List<CdpProfile.IdentityLink> identityTimeline(String anonymousId) {
    return jdbc.query(
        """
        select anonymous_id,customer_id,linked_at,link_method,correlation_id
        from identity_links where anonymous_id = ? order by linked_at
        """,
        (result, row) ->
            new CdpProfile.IdentityLink(
                result.getString("anonymous_id"),
                result.getString("customer_id"),
                result.getTimestamp("linked_at").toInstant(),
                result.getString("link_method"),
                result.getString("correlation_id")),
        anonymousId);
  }

  private String attributes(EventEnvelope event) {
    try {
      return mapper.writeValueAsString(Map.of("lastEvent", event.eventName()));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialize CDP attributes", exception);
    }
  }
}
