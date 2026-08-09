package com.aurora.context;

import com.aurora.cdp.SimulatedCdpAdapter;
import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalSnapshot;
import com.aurora.decision.DecisionEngine;
import com.aurora.experiments.ExperimentService;
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
  private final ContextCache cache;
  private final ExperimentService experiments;

  public ContextService(
      EventRepository events,
      SignalEngine signals,
      SimulatedCdpAdapter cdp,
      DecisionEngine decisions,
      ContextCache cache,
      ExperimentService experiments) {
    this.events = events;
    this.signals = signals;
    this.cdp = cdp;
    this.decisions = decisions;
    this.cache = cache;
    this.experiments = experiments;
  }

  public CustomerContext forSession(String sessionId) {
    CustomerContext cached = cache.get(sessionId);
    if (cached != null) return cached;
    List<EventEnvelope> recent = events.findBySession(sessionId);
    if (recent.isEmpty()) {
      CustomerContext context =
          new CustomerContext(
              cdp.profile("unknown"), sessionId, List.of(), List.of(), "Discovery", false, null);
      cache.put(sessionId, context);
      return context;
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
            profile,
            activeSignals,
            eligible,
            recent.get(recent.size() - 1).correlationId());
    experiments.recordExposure(decision, profile);
    CustomerContext context =
        new CustomerContext(
            profile,
            sessionId,
            recent,
            activeSignals,
            stage == null ? "Discovery" : stage.attributes().getOrDefault("stage", "Discovery"),
            eligible,
            decision);
    cache.put(sessionId, context);
    return context;
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

  public List<EventRepository.SessionSummary> sessions() {
    return events.recentSessions();
  }

  public java.util.Map<String, Object> qualityStats() {
    return events.qualityStats();
  }

  public java.util.Map<String, Long> funnel(String sessionId) {
    return events.funnel(sessionId);
  }
}
