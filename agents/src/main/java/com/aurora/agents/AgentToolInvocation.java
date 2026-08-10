package com.aurora.agents;

import java.util.List;
import java.util.UUID;

/**
 * A persisted tool result reference identifies the immutable result snapshot recorded for a call.
 */
public record AgentToolInvocation(
    UUID callId, String toolName, String resultReference, String status, Object result) {
  public <T> T resultAs(Class<T> type) {
    return type.cast(result);
  }

  public <T> List<T> resultAsList(Class<T> elementType) {
    if (!(result instanceof List<?> values)) {
      throw new IllegalStateException("Tool result is not a list");
    }
    return values.stream()
        .map(
            value -> {
              if (!elementType.isInstance(value)) {
                throw new IllegalStateException(
                    "Tool result contains " + value.getClass().getName());
              }
              return elementType.cast(value);
            })
        .toList();
  }
}
