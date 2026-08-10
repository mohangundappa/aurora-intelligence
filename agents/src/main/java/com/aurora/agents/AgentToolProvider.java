package com.aurora.agents;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Read-only tool surface consumed by agent runtimes and evaluation adapters. */
public interface AgentToolProvider {
  List<String> toolNames();

  Set<String> readOnlyToolNames();

  AgentToolInvocation invoke(String name, Object arguments, UUID executionId);
}
