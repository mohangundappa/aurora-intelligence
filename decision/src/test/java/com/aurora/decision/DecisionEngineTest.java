package com.aurora.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import com.aurora.common.martech.ActivationResult;
import com.aurora.common.martech.OfferDelivery;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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

  @Test
  void consentDeniedNeverReachesOfferDelivery() {
    OfferDelivery delivery = org.mockito.Mockito.mock(OfferDelivery.class);
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

    new DecisionEngine(new DecisionPolicy(), null, null, delivery)
        .decide("session", profile, List.of(), false, UUID.randomUUID().toString());

    org.mockito.Mockito.verifyNoInteractions(delivery);
  }

  @Test
  void rejectedOfferDeliveryIsSurfacedInDecisionReasoning() {
    OfferDelivery delivery = org.mockito.Mockito.mock(OfferDelivery.class);
    org.mockito.Mockito.when(delivery.deliver(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new ActivationResult(
                "web",
                "correlation",
                ActivationResult.Status.REJECTED,
                0,
                1,
                "provider rate limit",
                Map.of("provider", "test")));
    CdpProfile profile =
        new CdpProfile(
            "anon",
            null,
            new CdpProfile.Identity("anon", null, false),
            new CdpProfile.Loyalty("Guest", 0, false),
            new CdpProfile.ConsentState(true, true),
            Map.of(),
            Set.of(),
            List.of());

    Decision decision =
        new DecisionEngine(new DecisionPolicy(), null, null, delivery)
            .decide("session", profile, List.of(), true, "correlation");

    assertThat(decision.reasonCodes()).contains("MARTECH_ACTIVATION_REJECTED");
    assertThat(decision.explanation()).contains("provider rate limit");
  }

  @Test
  void deliveryExceptionDegradesToAUsableDecision() {
    OfferDelivery delivery =
        request -> {
          throw new IllegalStateException("provider unavailable");
        };
    Decision decision = personalizedDecision(delivery);

    assertThat(decision.experience()).isNotBlank();
    assertThat(decision.reasonCodes()).contains("MARTECH_ACTIVATION_FAILED");
    assertThat(decision.explanation()).contains("provider unavailable");
  }

  @Test
  void hangingDeliveryIsBoundedAndDegradesToAUsableDecision() {
    OfferDelivery delivery =
        request -> {
          try {
            TimeUnit.SECONDS.sleep(10);
          } catch (InterruptedException exception) {
            throw new IllegalStateException("interrupted", exception);
          }
          return null;
        };
    long started = System.nanoTime();

    Decision decision = personalizedDecision(delivery);

    assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2_000);
    assertThat(decision.experience()).isNotBlank();
    assertThat(decision.reasonCodes()).contains("MARTECH_ACTIVATION_FAILED");
    assertThat(decision.explanation()).contains("timed out");
  }

  private Decision personalizedDecision(OfferDelivery delivery) {
    CdpProfile profile =
        new CdpProfile(
            "anon",
            null,
            new CdpProfile.Identity("anon", null, false),
            new CdpProfile.Loyalty("Guest", 0, false),
            new CdpProfile.ConsentState(true, true),
            Map.of(),
            Set.of(),
            List.of());
    return new DecisionEngine(new DecisionPolicy(), null, null, delivery)
        .decide("session", profile, List.of(), true, UUID.randomUUID().toString());
  }
}
