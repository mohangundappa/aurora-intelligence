package com.aurora.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(
    @NotNull UUID eventId, @NotBlank String eventName, @NotNull Instant eventTime,
    @NotNull Instant receivedTime, @NotBlank String schemaVersion, @NotBlank String source,
    @NotBlank String sessionId, @NotBlank String anonymousId, String customerId,
    @NotBlank String correlationId, @NotNull @Valid Consent consent, @NotNull Map<String, Object> payload) {
  public record Consent(@NotNull Boolean analytics, @NotNull Boolean personalization) {}
}
