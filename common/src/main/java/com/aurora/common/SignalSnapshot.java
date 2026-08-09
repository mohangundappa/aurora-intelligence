package com.aurora.common;

import java.time.Instant;
import java.util.Map;

public record SignalSnapshot(
    String name,
    double value,
    double confidence,
    Instant computedAt,
    Instant expiresAt,
    String explanation,
    String provenance,
    Map<String, String> attributes,
    String sessionId,
    String customerId,
    String correlationId) {}
