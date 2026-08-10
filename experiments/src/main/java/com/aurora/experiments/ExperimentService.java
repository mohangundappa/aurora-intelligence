package com.aurora.experiments;

import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import com.aurora.common.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExperimentService {
  private static final Logger log = LoggerFactory.getLogger(ExperimentService.class);

  private final JdbcTemplate jdbc;
  private final ExperimentRegistry registry;

  public ExperimentService(JdbcTemplate jdbc, ExperimentRegistry registry) {
    this.jdbc = jdbc;
    this.registry = registry;
  }

  public ExperimentPerformance performance(String experimentId) {
    ExperimentDefinition definition = registry.definition(experimentId);
    java.util.List<ExperimentPerformance.Variant> variants =
        definition.variants().stream().map(variant -> variant(definition, variant.name())).toList();
    boolean insufficient = preDeployment(definition) || insufficient(definition);
    String warning =
        "At least "
            + definition.minimumExposuresPerVariant()
            + " exposed subjects per variant are required before lift or significance is presented.";
    return new ExperimentPerformance(
        experimentId,
        definition.name(),
        definition.description(),
        definition.primaryOutcomeEvent(),
        definition.minimumExposuresPerVariant(),
        variants,
        insufficient,
        warning);
  }

  public java.util.List<ExperimentDefinition> definitions() {
    return registry.definitions();
  }

  public void recordExposure(Decision decision, CdpProfile profile) {
    if (decision.experimentId() == null) return;
    ExperimentDefinition definition = registry.definition(decision.experimentId());
    if (definition.lifecycleStatus() != ExperimentDefinition.LifecycleStatus.DEPLOYED) {
      log.warn(
          "Refusing exposure recording for non-deployed experiment {} with status {}",
          definition.id(),
          definition.lifecycleStatus());
      return;
    }
    String variant =
        ExperimentAssignment.assign(profile.anonymousId(), profile.customerId(), definition);
    if (variant == null) return;
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

  private ExperimentPerformance.Variant variant(ExperimentDefinition definition, String variant) {
    Integer exposed =
        jdbc.queryForObject(
            "select count(*) from experiment_exposures where experiment_id=? and variant=?",
            Integer.class,
            definition.id(),
            variant);
    Integer clicks = outcome(definition.id(), variant, "OFFER_CLICKED");
    Integer starts = outcome(definition.id(), variant, "BOOKING_STARTED");
    Integer completions = outcome(definition.id(), variant, definition.primaryOutcomeEvent());
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

  private boolean insufficient(ExperimentDefinition definition) {
    return definition.variants().stream()
        .mapToInt(variant -> countExposures(definition.id(), variant.name()))
        .anyMatch(count -> count < definition.minimumExposuresPerVariant());
  }

  private boolean preDeployment(ExperimentDefinition definition) {
    return switch (definition.lifecycleStatus()) {
      case DRAFT, TESTED, APPROVED -> true;
      case DEPLOYED, RETIRED -> false;
    };
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
        || registry.definitions().stream()
            .anyMatch(definition -> definition.primaryOutcomeEvent().equals(eventName));
  }
}
