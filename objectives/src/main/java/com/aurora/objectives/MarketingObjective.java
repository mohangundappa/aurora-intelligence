package com.aurora.objectives;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record MarketingObjective(
    String objectiveId,
    String name,
    String description,
    String businessGoal,
    String targetKpi,
    BigDecimal targetValue,
    String targetAudience,
    Map<String, Object> constraints,
    LocalDate startDate,
    LocalDate endDate,
    Status status,
    String createdBy,
    Instant createdAt) {
  public MarketingObjective {
    require(objectiveId, "objectiveId");
    require(name, "name");
    require(description, "description");
    require(businessGoal, "businessGoal");
    require(targetKpi, "targetKpi");
    if (targetValue == null || targetValue.signum() < 0) {
      throw new IllegalArgumentException("targetValue must be zero or greater");
    }
    require(targetAudience, "targetAudience");
    if (constraints == null) throw new IllegalArgumentException("constraints is required");
    if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("startDate and endDate must define a valid range");
    }
    if (status == null) throw new IllegalArgumentException("status is required");
    require(createdBy, "createdBy");
    if (createdAt == null) throw new IllegalArgumentException("createdAt is required");
  }

  private static void require(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  public enum Status {
    DRAFT,
    ACTIVE,
    COMPLETED,
    ARCHIVED
  }
}
