package com.aurora.context;

import com.aurora.cdp.SimulatedCdpAdapter;
import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalSnapshot;
import com.aurora.decision.DecisionEngine;
import com.aurora.ingest.EventRepository;
import com.aurora.signals.SignalEngine;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ContextService {
  private final EventRepository events;
  private final SignalEngine signals;
  private final SimulatedCdpAdapter cdp;
  private final DecisionEngine decisions;

  public ContextService(
      EventRepository events,
      SignalEngine signals,
      SimulatedCdpAdapter cdp,
      DecisionEngine decisions) {
    this.events = events;
    this.signals = signals;
    this.cdp = cdp;
    this.decisions = decisions;
  }

  public CustomerContext forSession(String sessionId) {
    List<EventEnvelope> recent = events.findBySession(sessionId);
    if (recent.isEmpty()) {
      return new CustomerContext(
          cdp.profile("unknown"), sessionId, List.of(), List.of(), "Discovery", false, null);
    }
    String anonymousId = recent.get(0).anonymousId();
    CdpProfile profile = cdp.profile(anonymousId);
    List<SignalSnapshot> activeSignals = signals.calculateAll(sessionId);
    SignalSnapshot stage =
        activeSignals.stream()
            .filter(signal -> "journey-stage".equals(signal.name()))
            .findFirst()
            .orElse(null);
    boolean eligible = profile.consent().personalization();
    Decision decision =
        decisions.decide(
            sessionId,
            activeSignals.stream()
                .filter(signal -> !"journey-stage".equals(signal.name()))
                .findFirst()
                .orElse(null),
            eligible,
            recent.get(recent.size() - 1).correlationId());
    return new CustomerContext(
        profile,
        sessionId,
        recent,
        activeSignals,
        stage == null
            ? "Discovery"
            : stage
                .explanation()
                .replace("Journey stage is derived from the furthest observed funnel event: ", "")
                .replace(".", ""),
        eligible,
        decision);
  }

  public CustomerContext forCustomer(String customerId) {
    return forSession(
        events.findByCustomer(customerId).stream()
            .findFirst()
            .map(EventEnvelope::sessionId)
            .orElse(""));
  }

  public List<EventEnvelope> events(String sessionId) {
    return events.findBySession(sessionId);
  }

  public List<com.aurora.common.SignalDefinition> definitions() {
    return signals.registryDefinitions();
  }
}
