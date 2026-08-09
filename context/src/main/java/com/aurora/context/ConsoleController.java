package com.aurora.context;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/console")
public class ConsoleController {
  private final ContextService context;

  public ConsoleController(ContextService context) {
    this.context = context;
  }

  @GetMapping("/sessions/{sessionId}")
  public Map<String, Object> session(@PathVariable String sessionId) {
    CustomerContext journey = context.forSession(sessionId);
    return Map.of(
        "events", journey.recentBehaviors(),
        "context", journey,
        "decision", journey.recommendedAction(),
        "definitions", context.definitions());
  }
}
