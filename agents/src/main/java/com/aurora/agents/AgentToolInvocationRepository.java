package com.aurora.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
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
          call_id,tool_name,arguments,result_reference,result_snapshot,status,
          started_at,completed_at)
        values (?,? ,?::jsonb,? ,?::jsonb,?,?,?)
        """,
        callId,
        toolName,
        json(arguments),
        resultReference,
        json(result),
        status,
        Timestamp.from(startedAt),
        Timestamp.from(completedAt));
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize agent tool evidence", exception);
    }
  }
}
