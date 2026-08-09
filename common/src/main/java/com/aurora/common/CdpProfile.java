package com.aurora.common;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CdpProfile(
    String anonymousId,
    String customerId,
    Identity identity,
    Loyalty loyalty,
    ConsentState consent,
    Map<String, String> attributes,
    Set<String> audiences,
    List<IdentityLink> identityTimeline) {
  public record Identity(String anonymousId, String customerId, boolean identified) {}

  public record Loyalty(String tier, int points, boolean eligible) {}

  public record ConsentState(boolean analytics, boolean personalization) {}

  public record IdentityLink(
      String anonymousId,
      String customerId,
      Instant linkedAt,
      String source,
      String correlationId) {}
}
