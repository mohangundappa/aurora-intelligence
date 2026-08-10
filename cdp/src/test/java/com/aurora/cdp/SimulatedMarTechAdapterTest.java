package com.aurora.cdp;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurora.common.martech.ActivationRequest;
import com.aurora.common.martech.ActivationResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimulatedMarTechAdapterTest {
  private final SimulatedMarTechAdapter adapter = new SimulatedMarTechAdapter();

  @Test
  void repeatedIdempotencyKeyReturnsTheOriginalResult() {
    ActivationRequest request =
        new ActivationRequest("booking-destination", Map.of("requestedCount", 10), "same-key");

    ActivationResult first = adapter.activate(request);
    ActivationResult retry =
        adapter.activate(
            new ActivationRequest(
                "booking-destination", Map.of("simulation", "REJECTED"), "same-key"));

    assertThat(first).isEqualTo(retry);
    assertThat(first.status()).isEqualTo(ActivationResult.Status.ACCEPTED);
    assertThat(first.acceptedCount()).isEqualTo(10);
  }

  @Test
  void rejectionAndPartialResultsCarryReasonsAndCounts() {
    ActivationResult rejected =
        adapter.deliver(
            new ActivationRequest(
                "offer-destination",
                Map.of("simulation", "REJECTED", "rejectionReason", "rate limited"),
                "reject-key"));
    ActivationResult partial =
        adapter.register(
            new ActivationRequest(
                "campaign-destination",
                Map.of("simulation", "PARTIAL", "requestedCount", 5),
                "partial-key"));

    assertThat(rejected.status()).isEqualTo(ActivationResult.Status.REJECTED);
    assertThat(rejected.reason()).isEqualTo("rate limited");
    assertThat(partial.status()).isEqualTo(ActivationResult.Status.PARTIAL);
    assertThat(partial.acceptedCount()).isEqualTo(2);
    assertThat(partial.rejectedCount()).isEqualTo(3);
  }
}
