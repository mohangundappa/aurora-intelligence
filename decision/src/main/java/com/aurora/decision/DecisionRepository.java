package com.aurora.decision;

import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import com.aurora.common.SignalSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DecisionRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public DecisionRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void save(Decision decision, CdpProfile profile, List<SignalSnapshot> signals) {
    try {
      jdbc.update(
          """
          insert into decisions
            (action,experience,channel,reason_codes,decision_version,experiment_id,explanation,
             session_id,correlation_id,inputs)
          values (?,?,?,?::jsonb,?,?,?, ?,?,?::jsonb)
          """,
          decision.action(),
          decision.experience(),
          decision.channel(),
          mapper.writeValueAsString(decision.reasonCodes()),
          decision.decisionVersion(),
          decision.experimentId(),
          decision.explanation(),
          decision.sessionId(),
          decision.correlationId(),
          mapper.writeValueAsString(new DecisionInputs(profile, signals)));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to persist decision", exception);
    }
  }

  private record DecisionInputs(CdpProfile profile, List<SignalSnapshot> signals) {}
}
