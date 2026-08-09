package com.aurora.models;

import java.util.List;
import java.util.Map;

public record ModelVersion(
    String modelName,
    String version,
    String status,
    List<String> features,
    Map<String, Double> weights,
    double bias) {}
