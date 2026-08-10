package com.aurora.experiments;

import com.aurora.common.martech.ActivationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ActivationAttemptRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public ActivationAttemptRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void save(ActivationAttempt attempt) {
    try {
      jdbc.update(
          """
          insert into martech_activation_attempts
            (attempt_id,proposal_id,operation,destination_id,payload,idempotency_key,
             status,accepted_count,rejected_count,reason,provider_metadata,attempted_at)
          values (?,?,?,?,?::jsonb,?,?,?,?,?,?::jsonb,?)
          """,
          attempt.attemptId(),
          attempt.proposalId(),
          attempt.operation(),
          attempt.destinationId(),
          mapper.writeValueAsString(attempt.payload()),
          attempt.idempotencyKey(),
          attempt.status().name(),
          attempt.acceptedCount(),
          attempt.rejectedCount(),
          attempt.reason(),
          mapper.writeValueAsString(attempt.providerMetadata()),
          Timestamp.from(attempt.attemptedAt()));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to persist MarTech activation attempt", exception);
    }
  }

  public List<ActivationAttempt> findByProposalId(UUID proposalId) {
    return jdbc.query(
        """
        select attempt_id,proposal_id,operation,destination_id,payload,idempotency_key,
               status,accepted_count,rejected_count,reason,provider_metadata,attempted_at
        from martech_activation_attempts where proposal_id = ? order by attempted_at, attempt_id
        """,
        (result, row) -> {
          try {
            return new ActivationAttempt(
                result.getObject("attempt_id", UUID.class),
                result.getObject("proposal_id", UUID.class),
                result.getString("operation"),
                result.getString("destination_id"),
                mapper.readValue(result.getString("payload"), Map.class),
                result.getString("idempotency_key"),
                ActivationResult.Status.valueOf(result.getString("status")),
                result.getInt("accepted_count"),
                result.getInt("rejected_count"),
                result.getString("reason"),
                mapper.readValue(result.getString("provider_metadata"), Map.class),
                result.getTimestamp("attempted_at").toInstant());
          } catch (Exception exception) {
            throw new IllegalStateException("Unable to read MarTech activation attempt", exception);
          }
        },
        proposalId);
  }
}
