package com.aurora.decision;

import com.aurora.common.*;
import org.springframework.stereotype.Service;

@Service
public class DecisionEngine {
  public Decision decide(
      String sessionId, SignalResult signal, boolean personalizationConsent, String correlationId) {
    if (!personalizationConsent)
      return new Decision(
          "STANDARD_WELCOME",
          java.util.List.of("CONSENT_NOT_GRANTED", "SAFE_DEFAULT"),
          "Personalization is disabled, so the standard welcome experience is shown.",
          sessionId,
          correlationId);
    if (signal != null)
      return new Decision(
          "MIAMI_GETAWAY",
          java.util.List.of("DESTINATION_INTENT_HIGH", "ACTIVE_SEARCH"),
          signal.explanation(),
          sessionId,
          signal.correlationId());
    return new Decision(
        "STANDARD_WELCOME",
        java.util.List.of("NO_ELIGIBLE_SIGNAL"),
        "No eligible signal was available.",
        sessionId,
        correlationId);
  }
}
