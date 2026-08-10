package com.aurora.experiments;

import com.aurora.common.martech.ActivationResult;

public class MarTechActivationException extends RuntimeException {
  private final String operation;
  private final ActivationResult result;

  public MarTechActivationException(String operation, ActivationResult result) {
    super(
        "Marketing platform "
            + operation
            + " activation "
            + result.status()
            + " for destination '"
            + result.destinationId()
            + "': "
            + result.reason());
    this.operation = operation;
    this.result = result;
  }

  public String operation() {
    return operation;
  }

  public ActivationResult result() {
    return result;
  }
}
