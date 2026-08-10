package com.aurora.experiments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ExperimentProposalRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public ExperimentProposalRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional
  public void save(ExperimentProposal proposal) {
    jdbc.update(
        """
        insert into experiment_proposals(
          proposal_id,objective_id,insight_id,experiment_id,experiment_name,
          experiment_description,target_audience,targeting_signal,hypothesis,primary_outcome_event,
          minimum_exposures_per_variant,expected_effect,reasoning,evidence_refs,
          correlation_id,governance_state,created_at)
        values (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)
        """,
        proposal.proposalId(),
        proposal.objectiveId(),
        proposal.insightId(),
        proposal.experimentId(),
        proposal.experimentName(),
        proposal.experimentDescription(),
        proposal.targetAudience(),
        proposal.targetingSignal(),
        proposal.hypothesis(),
        proposal.primaryOutcomeEvent(),
        proposal.minimumExposuresPerVariant(),
        proposal.expectedEffect(),
        proposal.reasoning(),
        json(proposal.evidenceRefs()),
        proposal.correlationId(),
        proposal.governanceState().name(),
        Timestamp.from(proposal.createdAt()));
    for (int ordinal = 0; ordinal < proposal.variants().size(); ordinal++) {
      ExperimentProposal.Variant variant = proposal.variants().get(ordinal);
      jdbc.update(
          """
          insert into experiment_proposal_variants(
            proposal_id,variant_name,allocation_percentage,variant_order)
          values (?,?,?,?)
          """,
          proposal.proposalId(),
          variant.name(),
          variant.allocationPercentage(),
          ordinal);
    }
  }

  public Optional<ExperimentProposal> findById(UUID proposalId) {
    return jdbc
        .query(
            "select * from experiment_proposals where proposal_id=?",
            (result, row) -> map(result),
            proposalId)
        .stream()
        .findFirst();
  }

  public List<ExperimentProposal> findAll() {
    return jdbc.query(
        "select * from experiment_proposals order by created_at desc",
        (result, row) -> map(result));
  }

  @Transactional
  public void transition(
      UUID proposalId,
      ExperimentProposal.GovernanceState from,
      ExperimentProposal.GovernanceState to,
      String actor,
      String reason) {
    int updated =
        jdbc.update(
            "update experiment_proposals set governance_state=?,updated_at=now() "
                + "where proposal_id=? and governance_state=?",
            to.name(),
            proposalId,
            from.name());
    if (updated != 1) {
      throw new IllegalStateException("Proposal state changed while processing transition");
    }
    jdbc.update(
        """
        insert into experiment_governance_audit(
          proposal_id,action,actor,from_state,to_state,reason)
        values (?,?,?,?,?,?)
        """,
        proposalId,
        to.name(),
        actor,
        from.name(),
        to.name(),
        reason);
  }

  public List<GovernanceAudit> audit(UUID proposalId) {
    return jdbc.query(
        """
        select proposal_id,action,actor,from_state,to_state,reason,created_at
        from experiment_governance_audit where proposal_id=? order by created_at
        """,
        (result, row) ->
            new GovernanceAudit(
                result.getObject("proposal_id", UUID.class),
                result.getString("action"),
                result.getString("actor"),
                result.getString("from_state"),
                result.getString("to_state"),
                result.getString("reason"),
                result.getTimestamp("created_at").toInstant()),
        proposalId);
  }

  private ExperimentProposal map(ResultSet result) throws SQLException {
    UUID proposalId = result.getObject("proposal_id", UUID.class);
    List<ExperimentProposal.Variant> variants =
        jdbc.query(
            """
                select variant_name,allocation_percentage
                from experiment_proposal_variants
                where proposal_id=? order by variant_order
                """,
            (variant, row) ->
                new ExperimentProposal.Variant(
                    variant.getString("variant_name"), variant.getInt("allocation_percentage")),
            proposalId);
    return new ExperimentProposal(
        proposalId,
        result.getString("objective_id"),
        result.getObject("insight_id", UUID.class),
        result.getString("experiment_id"),
        result.getString("experiment_name"),
        result.getString("experiment_description"),
        result.getString("target_audience"),
        result.getString("targeting_signal"),
        result.getString("hypothesis"),
        variants,
        result.getString("primary_outcome_event"),
        result.getInt("minimum_exposures_per_variant"),
        result.getObject("expected_effect", BigDecimal.class),
        result.getString("reasoning"),
        readList(result.getString("evidence_refs")),
        result.getString("correlation_id"),
        ExperimentProposal.GovernanceState.valueOf(result.getString("governance_state")),
        result.getTimestamp("created_at").toInstant());
  }

  private List<String> readList(String value) {
    try {
      return mapper.readerForListOf(String.class).readValue(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read proposal evidence references", exception);
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize proposal evidence", exception);
    }
  }

  public record GovernanceAudit(
      UUID proposalId,
      String action,
      String actor,
      String fromState,
      String toState,
      String reason,
      java.time.Instant createdAt) {}
}
