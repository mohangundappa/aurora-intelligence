package com.aurora.agents;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MarketingInsight(
    UUID insightId,
    String objectiveId,
    String subject,
    String finding,
    Map<String, Object> metrics,
    List<String> evidenceRefs,
    String correlationId,
    Instant createdAt) {
  public MarketingInsight {
    if (insightId == null) throw new IllegalArgumentException("insightId is required");
    if (objectiveId == null || objectiveId.isBlank()) {
      throw new IllegalArgumentException("objectiveId is required");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
    if (finding == null || finding.isBlank()) {
      throw new IllegalArgumentException("finding is required");
    }
    if (metrics == null || metrics.isEmpty()) {
      throw new IllegalArgumentException("metrics are required");
    }
    if (evidenceRefs == null || evidenceRefs.isEmpty()) {
      throw new IllegalArgumentException("evidenceRefs are required");
    }
    if (correlationId == null || correlationId.isBlank()) {
      throw new IllegalArgumentException("correlationId is required");
    }
    if (createdAt == null) throw new IllegalArgumentException("createdAt is required");
    metrics = Map.copyOf(metrics);
    evidenceRefs = List.copyOf(evidenceRefs);
  }
}
