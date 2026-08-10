package com.aurora.objectives;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkflowStageTimingService {
  private final JdbcTemplate jdbc;

  public WorkflowStageTimingService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public WorkflowStageTiming record(
      String objectiveId,
      WorkflowStage stage,
      long elapsedMilliseconds,
      String recordedBy,
      Instant startedAt,
      Instant completedAt) {
    if (objectiveId == null || objectiveId.isBlank()) {
      throw new IllegalArgumentException("objectiveId is required");
    }
    if (stage == null) throw new IllegalArgumentException("stage is required");
    if (elapsedMilliseconds < 0) {
      throw new IllegalArgumentException("elapsedMilliseconds must be zero or greater");
    }
    if (recordedBy == null || recordedBy.isBlank()) {
      throw new IllegalArgumentException("recordedBy is required");
    }
    if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("startedAt and completedAt must define a valid range");
    }
    UUID timingId = UUID.randomUUID();
    Instant createdAt = Instant.now();
    jdbc.update(
        """
        insert into workflow_stage_timings(
          timing_id,objective_id,stage,elapsed_milliseconds,recorded_by,
          started_at,completed_at,created_at)
        values (?,?,?,?,?,?,?,?)
        """,
        timingId,
        objectiveId,
        stage.name(),
        elapsedMilliseconds,
        recordedBy,
        java.sql.Timestamp.from(startedAt),
        java.sql.Timestamp.from(completedAt),
        java.sql.Timestamp.from(createdAt));
    return new WorkflowStageTiming(
        timingId,
        objectiveId,
        stage,
        elapsedMilliseconds,
        recordedBy,
        startedAt,
        completedAt,
        createdAt);
  }
}
