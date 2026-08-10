package com.aurora.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
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

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize marketing insight", exception);
    }
  }
}
