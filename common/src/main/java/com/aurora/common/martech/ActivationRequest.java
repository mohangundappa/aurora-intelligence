package com.aurora.common.martech;

import java.util.Map;

public record ActivationRequest(
    String destinationId, Map<String, Object> payload, String idempotencyKey) {
  public ActivationRequest {
    if (destinationId == null || destinationId.isBlank()) {
      throw new IllegalArgumentException("destinationId is required");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload is required");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
    payload = Map.copyOf(payload);
  }
}
