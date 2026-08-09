package com.aurora.common;

import java.time.Instant;

public record SignalSnapshot(
    String name,
    double value,
    double confidence,
    Instant computedAt,
    Instant expiresAt,
    String explanation,
    String provenance,
    String sessionId,
    String customerId,
    String correlationId) {}
