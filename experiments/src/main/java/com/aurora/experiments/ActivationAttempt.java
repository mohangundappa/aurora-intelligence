package com.aurora.experiments;

import com.aurora.common.martech.ActivationRequest;
import com.aurora.common.martech.ActivationResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ActivationAttempt(
    UUID attemptId,
    UUID proposalId,
    String contextId,
    String operation,
    String destinationId,
    Map<String, Object> payload,
    String idempotencyKey,
    ActivationResult.Status status,
    int acceptedCount,
    int rejectedCount,
    String reason,
    Map<String, String> providerMetadata,
    Instant attemptedAt) {
  public ActivationAttempt {
    if (attemptId == null) throw new IllegalArgumentException("attemptId is required");
    if (proposalId == null && (contextId == null || contextId.isBlank())) {
      throw new IllegalArgumentException("proposalId or contextId is required");
    }
    if (operation == null || operation.isBlank()) {
      throw new IllegalArgumentException("operation is required");
    }
    if (destinationId == null || destinationId.isBlank()) {
      throw new IllegalArgumentException("destinationId is required");
    }
    if (payload == null) throw new IllegalArgumentException("payload is required");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
    if (status == null) throw new IllegalArgumentException("status is required");
    if (acceptedCount < 0 || rejectedCount < 0) {
      throw new IllegalArgumentException("activation counts cannot be negative");
    }
    if (attemptedAt == null) throw new IllegalArgumentException("attemptedAt is required");
    payload = Map.copyOf(payload);
    providerMetadata = Map.copyOf(providerMetadata == null ? Map.of() : providerMetadata);
  }

  public static ActivationAttempt from(
      UUID proposalId, String operation, ActivationRequest request, ActivationResult result) {
    return new ActivationAttempt(
        UUID.randomUUID(),
        proposalId,
        null,
        operation,
        request.destinationId(),
        request.payload(),
        request.idempotencyKey(),
        result.status(),
        result.acceptedCount(),
        result.rejectedCount(),
        result.reason(),
        result.providerMetadata(),
        Instant.now());
  }

  public static ActivationAttempt fromContext(
      String contextId, String operation, ActivationRequest request, ActivationResult result) {
    return new ActivationAttempt(
        UUID.randomUUID(),
        null,
        contextId,
        operation,
        request.destinationId(),
        request.payload(),
        request.idempotencyKey(),
        result.status(),
        result.acceptedCount(),
        result.rejectedCount(),
        result.reason(),
        result.providerMetadata(),
        Instant.now());
  }
}
