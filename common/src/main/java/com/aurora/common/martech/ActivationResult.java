package com.aurora.common.martech;

import java.util.Map;

public record ActivationResult(
    String destinationId,
    String idempotencyKey,
    Status status,
    int acceptedCount,
    int rejectedCount,
    String reason,
    Map<String, String> providerMetadata) {
  public ActivationResult {
    if (destinationId == null || destinationId.isBlank()) {
      throw new IllegalArgumentException("destinationId is required");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
    if (status == null) throw new IllegalArgumentException("status is required");
    if (acceptedCount < 0 || rejectedCount < 0) {
      throw new IllegalArgumentException("activation counts cannot be negative");
    }
    if (requiresReason(status) && (reason == null || reason.isBlank())) {
      throw new IllegalArgumentException(status + " activations require a reason");
    }
    providerMetadata = Map.copyOf(providerMetadata == null ? Map.of() : providerMetadata);
  }

  public enum Status {
    ACCEPTED,
    REJECTED,
    PARTIAL,
    FAILED,
    UNCONFIGURED
  }

  private static boolean requiresReason(Status status) {
    return status == Status.REJECTED || status == Status.FAILED || status == Status.UNCONFIGURED;
  }
}
