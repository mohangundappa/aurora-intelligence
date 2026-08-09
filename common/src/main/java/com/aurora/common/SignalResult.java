package com.aurora.common;

public record SignalResult(String name, double value, double confidence, String freshness, String explanation,
                           String sessionId, String correlationId) {}
