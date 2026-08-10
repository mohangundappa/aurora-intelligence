package com.aurora.experiments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExperimentAssignment {
  private static final Logger log = LoggerFactory.getLogger(ExperimentAssignment.class);

  private ExperimentAssignment() {}

  public static String stableSubjectId(String anonymousId, String customerId) {
    return anonymousId != null && !anonymousId.isBlank() ? anonymousId : customerId;
  }

  public static String assign(
      String anonymousId, String customerId, ExperimentDefinition definition) {
    if (definition.lifecycleStatus() != ExperimentDefinition.LifecycleStatus.DEPLOYED) {
      log.warn(
          "Refusing experiment assignment for non-deployed experiment {} with status {}",
          definition.id(),
          definition.lifecycleStatus());
      return null;
    }
    String subject = stableSubjectId(anonymousId, customerId);
    if (subject == null) throw new IllegalArgumentException("An experiment requires a subject ID");
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((subject + ":" + definition.id()).getBytes(StandardCharsets.UTF_8));
      long bucket = Long.parseLong(HexFormat.of().formatHex(digest).substring(0, 8), 16) % 100;
      long boundary = 0;
      for (ExperimentDefinition.Variant variant : definition.variants()) {
        boundary += variant.allocationPercentage();
        if (bucket < boundary) return variant.name();
      }
      throw new IllegalStateException("Experiment allocation did not cover bucket " + bucket);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to assign experiment variant", exception);
    }
  }
}
