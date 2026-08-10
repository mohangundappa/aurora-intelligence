package com.aurora.agents;

public final class AgentToolResults {
  private AgentToolResults() {}

  public record SignalObservation(
      String sessionId, boolean signalPresent, double signalValue, boolean converted) {}
}
