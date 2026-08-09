package com.aurora.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurora.common.Decision;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionEngineTest {
  @Test
  void consentOffReturnsSafeDefaultWithReasons() {
    Decision decision =
        new DecisionEngine()
            .decide(
                "session",
                (com.aurora.common.SignalResult) null,
                false,
                UUID.randomUUID().toString());

    assertThat(decision.experience()).isEqualTo("STANDARD_WELCOME");
    assertThat(decision.reasonCodes()).contains("CONSENT_NOT_GRANTED", "SAFE_DEFAULT");
  }
}
