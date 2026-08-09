package com.aurora.experiments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ExperimentAssignment {
  private ExperimentAssignment() {}

  public static String stableSubjectId(String anonymousId, String customerId) {
    return anonymousId != null && !anonymousId.isBlank() ? anonymousId : customerId;
  }

  public static String assign(String anonymousId, String customerId, String experimentId) {
    String subject = stableSubjectId(anonymousId, customerId);
    if (subject == null) throw new IllegalArgumentException("An experiment requires a subject ID");
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((subject + ":" + experimentId).getBytes(StandardCharsets.UTF_8));
      long bucket = Long.parseLong(HexFormat.of().formatHex(digest).substring(0, 8), 16) % 100;
      return bucket < 50 ? "control" : "treatment";
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to assign experiment variant", exception);
    }
  }
}
