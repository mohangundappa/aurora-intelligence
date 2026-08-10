package com.aurora.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MarketingInsightRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public MarketingInsightRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void save(MarketingInsight insight) {
    jdbc.update(
        """
        insert into marketing_insights(
          insight_id,objective_id,subject,finding,metrics,evidence_refs,correlation_id,created_at)
        values (?,?,?,?::text,?::jsonb,?::jsonb,?,?)
        """,
        insight.insightId(),
        insight.objectiveId(),
        insight.subject(),
        insight.finding(),
        json(insight.metrics()),
        json(insight.evidenceRefs()),
        insight.correlationId(),
        Timestamp.from(insight.createdAt()));
  }

  public Optional<MarketingInsight> findById(UUID insightId) {
    return jdbc
        .query(
            "select insight_id,objective_id,subject,finding,metrics,evidence_refs,correlation_id,created_at "
                + "from marketing_insights where insight_id=?",
            (result, row) ->
                new MarketingInsight(
                    result.getObject("insight_id", UUID.class),
                    result.getString("objective_id"),
                    result.getString("subject"),
                    result.getString("finding"),
                    readMap(result.getString("metrics")),
                    readList(result.getString("evidence_refs")),
                    result.getString("correlation_id"),
                    result.getTimestamp("created_at").toInstant()),
            insightId)
        .stream()
        .findFirst();
  }

  public List<MarketingInsight> findAll() {
    return jdbc.query(
        "select insight_id,objective_id,subject,finding,metrics,evidence_refs,correlation_id,created_at "
            + "from marketing_insights order by created_at desc",
        (result, row) ->
            new MarketingInsight(
                result.getObject("insight_id", UUID.class),
                result.getString("objective_id"),
                result.getString("subject"),
                result.getString("finding"),
                readMap(result.getString("metrics")),
                readList(result.getString("evidence_refs")),
                result.getString("correlation_id"),
                result.getTimestamp("created_at").toInstant()));
  }

  private java.util.Map<String, Object> readMap(String value) {
    try {
      return mapper.readValue(
          value,
          new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read marketing insight metrics", exception);
    }
  }

  private java.util.List<String> readList(String value) {
    try {
      return mapper.readerForListOf(String.class).readValue(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read marketing insight evidence", exception);
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize marketing insight", exception);
    }
  }
}
