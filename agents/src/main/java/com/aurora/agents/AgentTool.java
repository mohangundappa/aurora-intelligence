package com.aurora.agents;

public interface AgentTool<I, O> {
  String name();

  Class<I> inputType();

  boolean readOnly();

  O execute(I input);
}
