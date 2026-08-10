package com.aurora.agents;

import com.aurora.common.SignalSnapshot;
import java.util.List;
import java.util.Map;

public final class AgentToolInputs {
  private AgentToolInputs() {}

  public record Session(String sessionId) {}

  public record Signal(String signalName) {}

  public record Model(String modelName) {}

  public record ModelEvaluation(String modelName, String version) {}

  public record Experiment(String experimentId) {}

  public record Decision(List<SignalSnapshot> signals) {}

  public record SignalCalculation(
      String signalName, String conversionEvent, List<String> sessionIds) {}

  public record Empty() {
    public static final Empty INSTANCE = new Empty();
  }

  public record ModelFeatures(String modelName, Map<String, Double> features) {}
}
