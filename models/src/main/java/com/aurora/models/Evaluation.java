package com.aurora.models;

public record Evaluation(
    String modelName, String version, int examples, double accuracy, double meanAbsoluteError) {}
