package com.aurora.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionEngineTest {
  @Test
  void consentOffReturnsSafeDefaultWithReasons() {
    CdpProfile profile =
        new CdpProfile(
            "anon",
            null,
            new CdpProfile.Identity("anon", null, false),
            new CdpProfile.Loyalty("Guest", 0, false),
            new CdpProfile.ConsentState(true, false),
            Map.of(),
            Set.of(),
            List.of());
    Decision decision =
        new DecisionEngine(new DecisionPolicy())
            .decide("session", profile, List.of(), false, UUID.randomUUID().toString());

    assertThat(decision.experience()).isEqualTo("STANDARD_WELCOME");
    assertThat(decision.reasonCodes()).contains("CONSENT_NOT_GRANTED", "SAFE_DEFAULT");
  }
}
