package com.aurora.objectives;

import java.time.Instant;
import java.util.UUID;

public record WorkflowStageTiming(
    UUID timingId,
    String objectiveId,
    WorkflowStage stage,
    long elapsedMilliseconds,
    String recordedBy,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt) {}
