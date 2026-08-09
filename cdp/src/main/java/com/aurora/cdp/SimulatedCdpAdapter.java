package com.aurora.cdp;

import com.aurora.common.CdpProfile;
import com.aurora.common.EventEnvelope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SimulatedCdpAdapter implements CdpAdapter {
  private final Map<String, ProfileState> profiles = new ConcurrentHashMap<>();

  @Override
  public void accept(EventEnvelope event) {
    ProfileState state =
        profiles.computeIfAbsent(
            event.anonymousId(),
            key ->
                new ProfileState(
                    key,
                    null,
                    new CdpProfile.ConsentState(
                        event.consent().analytics(), event.consent().personalization())));
    state.attributes.put("lastEvent", event.eventName());
    state.consent =
        new CdpProfile.ConsentState(event.consent().analytics(), event.consent().personalization());
    if ("CUSTOMER_IDENTIFIED".equals(event.eventName())) {
      String customerId = String.valueOf(event.payload().get("customerId"));
      state.customerId = customerId;
      state.links.add(
          new CdpProfile.IdentityLink(
              event.anonymousId(),
              customerId,
              Instant.now(),
              "explicit-event",
              event.correlationId()));
    }
  }

  public CdpProfile profile(String anonymousId) {
    ProfileState state =
        profiles.getOrDefault(
            anonymousId,
            new ProfileState(anonymousId, null, new CdpProfile.ConsentState(false, false)));
    return new CdpProfile(
        anonymousId,
        state.customerId,
        new CdpProfile.Identity(anonymousId, state.customerId, state.customerId != null),
        new CdpProfile.Loyalty(
            state.customerId == null ? "Guest" : "Aurora Circle",
            state.customerId == null ? 0 : 1200,
            state.customerId != null),
        state.consent,
        Map.copyOf(state.attributes),
        new HashSet<>(state.audiences),
        new ArrayList<>(state.links));
  }

  private static final class ProfileState {
    private final String anonymousId;
    private String customerId;
    private CdpProfile.ConsentState consent;
    private final Map<String, String> attributes = new HashMap<>();
    private final java.util.Set<String> audiences = new HashSet<>();
    private final java.util.List<CdpProfile.IdentityLink> links = new ArrayList<>();

    private ProfileState(String anonymousId, String customerId, CdpProfile.ConsentState consent) {
      this.anonymousId = anonymousId;
      this.customerId = customerId;
      this.consent = consent;
    }
  }
}
