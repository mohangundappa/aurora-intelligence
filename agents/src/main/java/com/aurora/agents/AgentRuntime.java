package com.aurora.agents;

public interface AgentRuntime<I, O> {
  AgentRun<O> run(I input, String correlationId);

  record AgentRun<O>(O output, AgentExecution execution) {}
}
