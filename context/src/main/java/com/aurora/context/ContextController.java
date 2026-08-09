package com.aurora.context;

import com.aurora.common.SignalSnapshot;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ContextController {
  private final ContextService service;

  public ContextController(ContextService service) {
    this.service = service;
  }

  @GetMapping("/sessions/{sessionId}/journey")
  public CustomerContext journey(@PathVariable String sessionId) {
    return service.forSession(sessionId);
  }

  @GetMapping("/customers/{customerId}/context")
  public CustomerContext context(@PathVariable String customerId) {
    return service.forCustomer(customerId);
  }

  @GetMapping("/customers/{customerId}/signals")
  public List<SignalSnapshot> signals(@PathVariable String customerId) {
    return service.forCustomer(customerId).activeSignals();
  }

  @GetMapping("/sessions/{sessionId}/decision")
  public com.aurora.common.Decision decision(
      @PathVariable String sessionId, @RequestParam(defaultValue = "true") boolean consent) {
    CustomerContext customerContext = service.forSession(sessionId);
    if (!consent) {
      return new com.aurora.common.Decision(
          "STANDARD_WELCOME",
          List.of("CONSENT_NOT_GRANTED", "SAFE_DEFAULT"),
          "Personalization is disabled, so the standard welcome experience is shown.",
          sessionId,
          customerContext.recentBehaviors().stream()
              .findFirst()
              .map(event -> event.correlationId())
              .orElse(null));
    }
    return customerContext.recommendedAction();
  }

  @GetMapping("/signals/definitions")
  public List<com.aurora.common.SignalDefinition> definitions() {
    return service.definitions();
  }
}
