package com.aurora.objectives;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MarketingObjectiveRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public MarketingObjectiveRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void save(MarketingObjective objective) {
    jdbc.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  """
                  insert into marketing_objectives(
                    objective_id,name,description,business_goal,target_kpi,target_value,target_audience,
                    constraints,start_date,end_date,status,created_by,created_at,updated_at)
                  values (?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,now())
                  """);
          statement.setString(1, objective.objectiveId());
          statement.setString(2, objective.name());
          statement.setString(3, objective.description());
          statement.setString(4, objective.businessGoal());
          statement.setString(5, objective.targetKpi());
          statement.setBigDecimal(6, objective.targetValue());
          statement.setString(7, objective.targetAudience());
          statement.setString(8, json(objective.constraints()));
          statement.setObject(9, objective.startDate());
          statement.setObject(10, objective.endDate());
          statement.setString(11, objective.status().name());
          statement.setString(12, objective.createdBy());
          statement.setTimestamp(13, java.sql.Timestamp.from(objective.createdAt()));
          return statement;
        });
  }

  public Optional<MarketingObjective> findById(String objectiveId) {
    return jdbc
        .query(
            "select * from marketing_objectives where objective_id=?",
            (result, row) -> map(result),
            objectiveId)
        .stream()
        .findFirst();
  }

  public List<MarketingObjective> findAll() {
    return jdbc.query(
        "select * from marketing_objectives order by created_at desc",
        (result, row) -> map(result));
  }

  public String status(String objectiveId) {
    return jdbc.queryForObject(
        "select status from marketing_objectives where objective_id=?", String.class, objectiveId);
  }

  public void transition(String objectiveId, MarketingObjective.Status status) {
    jdbc.update(
        "update marketing_objectives set status=?,updated_at=now() where objective_id=?",
        status.name(),
        objectiveId);
  }

  public void audit(
      String objectiveId,
      String actor,
      MarketingObjective.Status from,
      MarketingObjective.Status to) {
    jdbc.update(
        """
        insert into marketing_objective_audit(
          objective_id,action,actor,from_status,to_status)
        values (?,?,?,?,?)
        """,
        objectiveId,
        to.name(),
        actor,
        from.name(),
        to.name());
  }

  private MarketingObjective map(ResultSet result) throws SQLException {
    try {
      return new MarketingObjective(
          result.getString("objective_id"),
          result.getString("name"),
          result.getString("description"),
          result.getString("business_goal"),
          result.getString("target_kpi"),
          result.getBigDecimal("target_value"),
          result.getString("target_audience"),
          mapper.readValue(result.getString("constraints"), MapType.TYPE),
          result.getObject("start_date", java.time.LocalDate.class),
          result.getObject("end_date", java.time.LocalDate.class),
          MarketingObjective.Status.valueOf(result.getString("status")),
          result.getString("created_by"),
          result.getTimestamp("created_at").toInstant());
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read marketing objective", exception);
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize objective constraints", exception);
    }
  }

  private static final class MapType {
    private static final com.fasterxml.jackson.core.type.TypeReference<
            java.util.Map<String, Object>>
        TYPE = new com.fasterxml.jackson.core.type.TypeReference<>() {};
  }
}
