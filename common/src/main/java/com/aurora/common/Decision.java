package com.aurora.common;

import java.util.List;

public record Decision(
    String experience,
    List<String> reasonCodes,
    String explanation,
    String sessionId,
    String correlationId) {}
