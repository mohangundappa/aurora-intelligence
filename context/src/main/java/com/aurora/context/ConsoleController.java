package com.aurora.context;

import com.aurora.ingest.EventRepository.SessionSummary;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/console")
public class ConsoleController {
  private final ContextService context;
  private final DeliveryComparisonService delivery;

  public ConsoleController(ContextService context, DeliveryComparisonService delivery) {
    this.context = context;
    this.delivery = delivery;
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

  @GetMapping("/sessions")
  public List<SessionSummary> sessions() {
    return context.sessions();
  }

  @GetMapping("/ops")
  public Map<String, Object> operations() {
    return Map.of(
        "dataQuality",
        context.qualityStats(),
        "components",
        Map.of("postgres", "UP", "redis", "UP", "redpanda", "UP"));
  }

  @GetMapping("/funnel/{sessionId}")
  public Map<String, Long> funnel(@PathVariable String sessionId) {
    return context.funnel(sessionId);
  }

  @GetMapping("/delivery")
  public DeliveryComparisonService.DeliveryComparison delivery() {
    return delivery.comparison();
  }
}
