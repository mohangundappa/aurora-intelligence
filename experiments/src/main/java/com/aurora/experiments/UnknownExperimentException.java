package com.aurora.experiments;

import java.util.List;

public class UnknownExperimentException extends IllegalArgumentException {
  public UnknownExperimentException(String id, List<String> registeredIds) {
    super("Unknown experiment '" + id + "'. Registered experiment ids: " + registeredIds);
  }
}
