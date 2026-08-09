package com.aurora.models;

import java.util.Map;

public record Prediction(
    String modelName,
    String modelVersion,
    double score,
    Map<String, Double> contributions,
    String explanation,
    double latencyMs) {}
