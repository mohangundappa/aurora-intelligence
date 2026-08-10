package com.aurora.experiments;

import com.aurora.common.martech.ActivationResult;
import java.util.UUID;

public class MarTechActivationException extends RuntimeException {
  private final String operation;
  private final ActivationResult result;
  private final UUID activationAttemptId;

  public MarTechActivationException(String operation, ActivationResult result) {
    this(operation, result, null);
  }

  public MarTechActivationException(
      String operation, ActivationResult result, UUID activationAttemptId) {
    super(
        "Marketing platform "
            + operation
            + " activation "
            + result.status()
            + " for destination '"
            + result.destinationId());
    this.operation = operation;
    this.result = result;
    this.activationAttemptId = activationAttemptId;
  }

  public String operation() {
    return operation;
  }

  public ActivationResult result() {
    return result;
  }

  public UUID activationAttemptId() {
    return activationAttemptId;
  }
}
