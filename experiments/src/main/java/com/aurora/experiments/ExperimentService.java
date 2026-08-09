package com.aurora.experiments;

import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import com.aurora.common.EventEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExperimentService {
  private final JdbcTemplate jdbc;

  public ExperimentService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public ExperimentPerformance performance(String experimentId) {
    java.util.List<ExperimentPerformance.Variant> variants =
        ExperimentDefinitions.variants(experimentId).stream()
            .map(variant -> variant(experimentId, variant))
            .toList();
    return new ExperimentPerformance(
        experimentId,
        variants.stream()
            .filter(variant -> "control".equals(variant.name()))
            .findFirst()
            .orElse(null),
        variants.stream()
            .filter(variant -> "treatment".equals(variant.name()))
            .findFirst()
            .orElse(null),
        variants,
        insufficient(experimentId),
        "At least 30 exposed subjects per variant are required before lift or significance is presented.");
  }

  public void recordExposure(Decision decision, CdpProfile profile) {
    if (decision.experimentId() == null) return;
    String variant =
        ExperimentAssignment.assign(
            profile.anonymousId(), profile.customerId(), decision.experimentId());
    jdbc.update(
        """
        insert into experiment_exposures(
          experiment_id,variant,subject_id,session_id,correlation_id)
        values (?,?,?,?,?)
        on conflict (correlation_id) do nothing
        """,
        decision.experimentId(),
        variant,
        ExperimentAssignment.stableSubjectId(profile.anonymousId(), profile.customerId()),
        decision.sessionId(),
        decision.correlationId());
  }

  public void recordOutcome(EventEnvelope event) {
    if (!isOutcome(event.eventName())) return;
    jdbc.update(
        """
        insert into experiment_outcomes(event_id,event_name,correlation_id,occurred_at)
        values (?,?,?,?)
        on conflict (event_id) do nothing
        """,
        event.eventId(),
        event.eventName(),
        event.correlationId(),
        java.sql.Timestamp.from(event.eventTime()));
  }

  private ExperimentPerformance.Variant variant(String experimentId, String variant) {
    Integer exposed =
        jdbc.queryForObject(
            "select count(*) from experiment_exposures where experiment_id=? and variant=?",
            Integer.class,
            experimentId,
            variant);
    Integer clicks = outcome(experimentId, variant, "OFFER_CLICKED");
    Integer starts = outcome(experimentId, variant, "BOOKING_STARTED");
    Integer completions = outcome(experimentId, variant, "BOOKING_COMPLETED");
    return new ExperimentPerformance.Variant(
        variant,
        exposed,
        clicks,
        starts,
        completions,
        exposed == 0 ? 0d : (double) completions / exposed);
  }

  private Integer outcome(String experimentId, String variant, String eventName) {
    return jdbc.queryForObject(
        """
        select count(*) from experiment_outcomes o
        join experiment_exposures x on x.correlation_id=o.correlation_id
        where x.experiment_id=? and x.variant=? and o.event_name=?
        """,
        Integer.class,
        experimentId,
        variant,
        eventName);
  }

  private boolean insufficient(String experimentId) {
    return ExperimentDefinitions.variants(experimentId).stream()
        .mapToInt(variant -> countExposures(experimentId, variant))
        .anyMatch(count -> count < 30);
  }

  private int countExposures(String experimentId, String variant) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from experiment_exposures where experiment_id=? and variant=?",
            Integer.class,
            experimentId,
            variant);
    return count == null ? 0 : count;
  }

  private boolean isOutcome(String eventName) {
    return "OFFER_CLICKED".equals(eventName)
        || "BOOKING_STARTED".equals(eventName)
        || "BOOKING_COMPLETED".equals(eventName);
  }
}
