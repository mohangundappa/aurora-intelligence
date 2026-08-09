package com.aurora.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurora.common.SignalSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionPolicyAttributeTest {
  @Test
  void destinationAttributeDrivesConfiguredRuleNotExplanationText() {
    SignalSnapshot signal =
        new SignalSnapshot(
            "destination-intent",
            80,
            0.8,
            Instant.now(),
            Instant.now().plusSeconds(60),
            "A reworded explanation with no place name.",
            "test",
            Map.of("destination", "Miami"),
            "session",
            null,
            "correlation");

    DecisionPolicy.DecisionPolicyResult result = new DecisionPolicy().evaluate(List.of(signal));

    assertThat(result.experience()).isEqualTo("MIAMI_GETAWAY");
    assertThat(result.reasonCodes()).contains("DESTINATION_INTENT_ELIGIBLE");
  }
}
