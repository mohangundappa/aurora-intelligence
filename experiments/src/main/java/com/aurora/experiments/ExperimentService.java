package com.aurora.experiments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExperimentService {
  private final JdbcTemplate jdbc;

  public ExperimentService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public ExperimentPerformance performance(String experimentId) {
    return new ExperimentPerformance(
        experimentId,
        variant(experimentId, "control"),
        variant(experimentId, "treatment"),
        insufficient(experimentId),
        "At least 30 exposed subjects per variant are required before lift or significance is presented.");
  }

  private ExperimentPerformance.Variant variant(String experimentId, String variant) {
    String predicate =
        "control".equals(variant)
            ? "mod(abs(hashtext(d.session_id)), 2) = 0"
            : "mod(abs(hashtext(d.session_id)), 2) = 1";
    Integer exposed =
        jdbc.queryForObject(
            "select count(*) from decisions d where d.experiment_id=? and " + predicate,
            Integer.class,
            experimentId);
    Integer clicks = outcome(experimentId, predicate, "OFFER_CLICKED");
    Integer starts = outcome(experimentId, predicate, "BOOKING_STARTED");
    Integer completions = outcome(experimentId, predicate, "BOOKING_COMPLETED");
    return new ExperimentPerformance.Variant(
        variant,
        exposed,
        clicks,
        starts,
        completions,
        exposed == 0 ? 0d : (double) completions / exposed);
  }

  private Integer outcome(String experimentId, String predicate, String eventName) {
    return jdbc.queryForObject(
        "select count(*) from raw_events e join decisions d on d.correlation_id=e.correlation_id where d.experiment_id=? and e.event_name=? and "
            + predicate.replace("d.", "d."),
        Integer.class,
        experimentId,
        eventName);
  }

  private boolean insufficient(String experimentId) {
    Integer minimum =
        jdbc.queryForObject(
            "select coalesce(min(count),0) from (select count(*) from decisions where experiment_id=? group by mod(abs(hashtext(session_id)),2)) groups(count)",
            Integer.class,
            experimentId);
    return minimum == null || minimum < 30;
  }
}
