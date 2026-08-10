package com.aurora.experiments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExperimentAnalysisRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public ExperimentAnalysisRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void save(ExperimentAnalysis analysis) {
    jdbc.update(
        """
        insert into experiment_analyses(
          analysis_id,experiment_id,variant_results,sufficient_sample,absolute_lift,
          relative_lift,recommendation,reasoning,evidence_refs,correlation_id,produced_at)
        values (?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
        """,
        analysis.analysisId(),
        analysis.experimentId(),
        json(analysis.variants()),
        analysis.sufficientSample(),
        analysis.absoluteLift(),
        analysis.relativeLift(),
        analysis.recommendation().name(),
        analysis.reasoning(),
        json(analysis.evidenceRefs()),
        analysis.correlationId(),
        Timestamp.from(analysis.producedAt()));
  }

  public Optional<ExperimentAnalysis> findById(UUID analysisId) {
    return jdbc
        .query(
            """
            select analysis_id,experiment_id,variant_results,sufficient_sample,absolute_lift,
                   relative_lift,recommendation,reasoning,evidence_refs,correlation_id,produced_at
            from experiment_analyses where analysis_id=?
            """,
            (result, row) ->
                map(
                    result.getObject("analysis_id", UUID.class),
                    result.getString("experiment_id"),
                    result.getString("variant_results"),
                    result.getBoolean("sufficient_sample"),
                    result.getBigDecimal("absolute_lift"),
                    result.getBigDecimal("relative_lift"),
                    result.getString("recommendation"),
                    result.getString("reasoning"),
                    result.getString("evidence_refs"),
                    result.getString("correlation_id"),
                    result.getTimestamp("produced_at").toInstant()),
            analysisId)
        .stream()
        .findFirst();
  }

  public List<ExperimentAnalysis> findByExperimentId(String experimentId) {
    return jdbc.query(
        """
        select analysis_id,experiment_id,variant_results,sufficient_sample,absolute_lift,
               relative_lift,recommendation,reasoning,evidence_refs,correlation_id,produced_at
        from experiment_analyses where experiment_id=? order by produced_at desc
        """,
        (result, row) ->
            map(
                result.getObject("analysis_id", UUID.class),
                result.getString("experiment_id"),
                result.getString("variant_results"),
                result.getBoolean("sufficient_sample"),
                result.getBigDecimal("absolute_lift"),
                result.getBigDecimal("relative_lift"),
                result.getString("recommendation"),
                result.getString("reasoning"),
                result.getString("evidence_refs"),
                result.getString("correlation_id"),
                result.getTimestamp("produced_at").toInstant()),
        experimentId);
  }

  private ExperimentAnalysis map(
      UUID analysisId,
      String experimentId,
      String variantResults,
      boolean sufficientSample,
      java.math.BigDecimal absoluteLift,
      java.math.BigDecimal relativeLift,
      String recommendation,
      String reasoning,
      String evidenceRefs,
      String correlationId,
      java.time.Instant producedAt) {
    try {
      return new ExperimentAnalysis(
          analysisId,
          experimentId,
          mapper.readValue(
              variantResults, new TypeReference<List<ExperimentAnalysis.VariantResult>>() {}),
          sufficientSample,
          absoluteLift,
          relativeLift,
          ExperimentAnalysis.Recommendation.valueOf(recommendation),
          reasoning,
          mapper.readValue(evidenceRefs, new TypeReference<List<String>>() {}),
          correlationId,
          producedAt);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read experiment analysis JSON", exception);
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize experiment analysis", exception);
    }
  }
}
