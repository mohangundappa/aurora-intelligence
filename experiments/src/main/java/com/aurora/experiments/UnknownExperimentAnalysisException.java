package com.aurora.experiments;

import java.util.UUID;

public class UnknownExperimentAnalysisException extends IllegalArgumentException {
  public UnknownExperimentAnalysisException(UUID analysisId) {
    super("Unknown experiment analysis " + analysisId);
  }
}
