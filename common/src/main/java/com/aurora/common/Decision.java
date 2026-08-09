package com.aurora.common;

import java.util.List;

public record Decision(
    String action,
    String experience,
    String channel,
    List<String> reasonCodes,
    String decisionVersion,
    String experimentId,
    String explanation,
    String sessionId,
    String correlationId) {
  public Decision(
      String experience,
      List<String> reasonCodes,
      String explanation,
      String sessionId,
      String correlationId) {
    this(
        experience,
        experience,
        "web",
        reasonCodes,
        "1.0",
        null,
        explanation,
        sessionId,
        correlationId);
  }
}
