package com.aurora.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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

@Repository
public class AgentExecutionRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final AgentToolInvocationRepository invocations;

  public AgentExecutionRepository(
      JdbcTemplate jdbc, ObjectMapper mapper, AgentToolInvocationRepository invocations) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.invocations = invocations;
  }

  public void save(AgentExecution execution) {
    jdbc.update(
        """
        insert into agent_executions(
          execution_id,objective_id,agent_type,model,model_version,started_at,completed_at,
          status,input_token_count,output_token_count,estimated_cost,latency_milliseconds,
          output_snapshot,errors,correlation_id)
        values (?,?,?,?,?,?,?,?,?,?,?, ?,?::jsonb,?::jsonb,?)
        on conflict (execution_id) do update set
          completed_at=excluded.completed_at,
          status=excluded.status,
          input_token_count=excluded.input_token_count,
          output_token_count=excluded.output_token_count,
          estimated_cost=excluded.estimated_cost,
          latency_milliseconds=excluded.latency_milliseconds,
          output_snapshot=excluded.output_snapshot,
          errors=excluded.errors,
          correlation_id=excluded.correlation_id
        """,
        execution.executionId(),
        execution.objectiveId(),
        execution.agentType(),
        execution.model(),
        execution.modelVersion(),
        Timestamp.from(execution.startedAt()),
        Timestamp.from(execution.completedAt()),
        execution.status(),
        execution.inputTokenCount(),
        execution.outputTokenCount(),
        execution.estimatedCost(),
        execution.latencyMilliseconds(),
        json(execution.output()),
        json(execution.errors()),
        execution.correlationId());
  }

  public List<AgentExecution> findAll() {
    return jdbc.query(
        """
        select execution_id,objective_id,agent_type,model,model_version,started_at,completed_at,
               status,input_token_count,output_token_count,estimated_cost,latency_milliseconds,
               output_snapshot,errors,correlation_id
        from agent_executions order by started_at desc
        """,
        (resultSet, row) -> map(resultSet));
  }

  public Optional<AgentExecution> findById(UUID executionId) {
    return jdbc
        .query(
            """
            select execution_id,objective_id,agent_type,model,model_version,started_at,completed_at,
                   status,input_token_count,output_token_count,estimated_cost,latency_milliseconds,
                   output_snapshot,errors,correlation_id
            from agent_executions where execution_id=?
            """,
            (resultSet, row) -> map(resultSet),
            executionId)
        .stream()
        .findFirst();
  }

  private AgentExecution map(ResultSet resultSet) throws SQLException {
    UUID executionId = resultSet.getObject("execution_id", UUID.class);
    return new AgentExecution(
        executionId,
        resultSet.getString("objective_id"),
        resultSet.getString("agent_type"),
        resultSet.getString("model"),
        resultSet.getString("model_version"),
        resultSet.getTimestamp("started_at").toInstant(),
        resultSet.getTimestamp("completed_at").toInstant(),
        resultSet.getString("status"),
        (Integer) resultSet.getObject("input_token_count"),
        (Integer) resultSet.getObject("output_token_count"),
        resultSet.getObject("estimated_cost", BigDecimal.class),
        resultSet.getLong("latency_milliseconds"),
        read(resultSet.getString("output_snapshot")),
        invocations.findByExecutionId(executionId),
        readList(resultSet.getString("errors")),
        resultSet.getString("correlation_id"));
  }

  private JsonNode read(String value) {
    try {
      return mapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read agent execution JSON", exception);
    }
  }

  private List<String> readList(String value) {
    try {
      return mapper.readerForListOf(String.class).readValue(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read agent execution errors", exception);
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize agent execution", exception);
    }
  }
}
