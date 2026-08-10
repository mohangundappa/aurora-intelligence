package com.aurora.agents;

import com.aurora.common.EventEnvelope;
import com.aurora.ingest.EventRepository;
import com.aurora.objectives.MarketingObjective;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InsightsAgent {
  private final AgentToolRegistry tools;

  public InsightsAgent(AgentToolRegistry tools) {
    this.tools = tools;
  }

  public MarketingInsight derive(
      MarketingObjective objective, UUID executionId, String correlationId) {
    AgentToolInvocation sessionsCall =
        tools.invoke("searchEvents", new AgentToolInputs.Session(null), executionId);
    @SuppressWarnings("unchecked")
    List<EventRepository.SessionSummary> sessions =
        (List<EventRepository.SessionSummary>) sessionsCall.result();
    if (sessions.isEmpty()) return null;

    List<String> evidenceRefs = new ArrayList<>();
    evidenceRefs.add(sessionsCall.resultReference());
    long completed = 0;
    long observedEvents = 0;
    for (EventRepository.SessionSummary session : sessions) {
      AgentToolInvocation eventsCall =
          tools.invoke(
              "searchEvents", new AgentToolInputs.Session(session.sessionId()), executionId);
      evidenceRefs.add(eventsCall.resultReference());
      @SuppressWarnings("unchecked")
      List<EventEnvelope> events = (List<EventEnvelope>) eventsCall.result();
      observedEvents += events.size();
      if (events.stream().anyMatch(event -> "BOOKING_COMPLETED".equals(event.eventName()))) {
        completed++;
      }
    }
    if (completed == 0) return null;

    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("observedSessions", sessions.size());
    metrics.put("bookingCompletedSessions", completed);
    metrics.put("observedEvents", observedEvents);
    metrics.put("observedCompletionRate", (double) completed / sessions.size());
    return new MarketingInsight(
        UUID.randomUUID(),
        objective.objectiveId(),
        "Observed leisure booking conversion signal",
        "Observed "
            + completed
            + " booking-completed sessions among "
            + sessions.size()
            + " recent sessions.",
        metrics,
        evidenceRefs,
        correlationId,
        Instant.now());
  }
}
