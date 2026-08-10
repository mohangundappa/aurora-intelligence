package com.aurora.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentToolInvocationRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public AgentToolInvocationRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void save(
      UUID callId,
      UUID executionId,
      String toolName,
      Object arguments,
      String resultReference,
      Object result,
      String status,
      Instant startedAt,
      Instant completedAt) {
    jdbc.update(
        """
        insert into agent_tool_calls(
          call_id,execution_id,tool_name,arguments,result_reference,result_snapshot,status,
          started_at,completed_at)
        values (?,?,? ,?::jsonb,? ,?::jsonb,?,?,?)
        """,
        callId,
        executionId,
        toolName,
        json(arguments),
        resultReference,
        json(result),
        status,
        Timestamp.from(startedAt),
        Timestamp.from(completedAt));
  }

  public List<AgentToolInvocation> findByExecutionId(UUID executionId) {
    return jdbc.query(
        """
        select call_id,tool_name,result_reference,status,result_snapshot
        from agent_tool_calls where execution_id=? order by started_at,call_id
        """,
        (resultSet, row) -> {
          try {
            return new AgentToolInvocation(
                resultSet.getObject("call_id", UUID.class),
                resultSet.getString("tool_name"),
                resultSet.getString("result_reference"),
                resultSet.getString("status"),
                mapper.readTree(resultSet.getString("result_snapshot")));
          } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read agent tool evidence", exception);
          }
        },
        executionId);
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize agent tool evidence", exception);
    }
  }
}
