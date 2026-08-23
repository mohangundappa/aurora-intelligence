package com.aurora.models;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ModelCandidate(
    UUID candidateId,
    String modelName,
    String packageHash,
    String studioInitiativeId,
    String status,
    Map<String, Object> packageContent,
    Instant createdAt) {}
