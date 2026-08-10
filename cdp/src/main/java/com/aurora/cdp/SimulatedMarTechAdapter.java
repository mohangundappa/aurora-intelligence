package com.aurora.cdp;

import com.aurora.common.martech.ActivationRequest;
import com.aurora.common.martech.ActivationResult;
import com.aurora.common.martech.AudienceActivation;
import com.aurora.common.martech.CampaignRegistration;
import com.aurora.common.martech.OfferDelivery;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SimulatedMarTechAdapter
    implements AudienceActivation, OfferDelivery, CampaignRegistration {
  private static final int MAX_RETAINED_RESULTS = 1_024;
  private final Map<String, ActivationResult> results =
      Collections.synchronizedMap(new LinkedHashMap<>());

  @Override
  public ActivationResult activate(ActivationRequest request) {
    return submit("AUDIENCE", request);
  }

  @Override
  public ActivationResult deliver(ActivationRequest request) {
    return submit("OFFER", request);
  }

  @Override
  public ActivationResult register(ActivationRequest request) {
    return submit("CAMPAIGN", request);
  }

  private ActivationResult submit(String operation, ActivationRequest request) {
    synchronized (results) {
      ActivationResult result =
          results.computeIfAbsent(
              request.idempotencyKey(),
              ignored -> {
                String simulation =
                    String.valueOf(request.payload().getOrDefault("simulation", "ACCEPTED"));
                ActivationResult.Status status =
                    switch (simulation.toUpperCase()) {
                      case "REJECTED" -> ActivationResult.Status.REJECTED;
                      case "PARTIAL" -> ActivationResult.Status.PARTIAL;
                      default -> ActivationResult.Status.ACCEPTED;
                    };
                int requested = requestedCount(request);
                int accepted = status == ActivationResult.Status.REJECTED ? 0 : requested;
                int rejected =
                    status == ActivationResult.Status.ACCEPTED ? 0 : requested - accepted;
                if (status == ActivationResult.Status.PARTIAL) {
                  accepted = Math.max(1, requested / 2);
                  rejected = requested - accepted;
                }
                String reason =
                    status == ActivationResult.Status.REJECTED
                        ? String.valueOf(
                            request
                                .payload()
                                .getOrDefault("rejectionReason", "simulated provider rejection"))
                        : status == ActivationResult.Status.PARTIAL
                            ? "simulated provider accepted only part of the request"
                            : null;
                return new ActivationResult(
                    request.destinationId(),
                    request.idempotencyKey(),
                    status,
                    accepted,
                    rejected,
                    reason,
                    Map.of("provider", "simulated", "operation", operation));
              });
      trimResults();
      return result;
    }
  }

  int retainedResultCount() {
    return results.size();
  }

  private void trimResults() {
    while (results.size() > MAX_RETAINED_RESULTS) {
      String oldestKey = results.keySet().iterator().next();
      results.remove(oldestKey);
    }
  }

  private int requestedCount(ActivationRequest request) {
    Object requested = request.payload().get("requestedCount");
    if (requested instanceof Number number && number.intValue() > 0) return number.intValue();
    return 1;
  }
}
