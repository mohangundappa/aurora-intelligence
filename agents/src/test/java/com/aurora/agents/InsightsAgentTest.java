package com.aurora.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurora.common.EventEnvelope;
import com.aurora.ingest.EventRepository;
import com.aurora.objectives.MarketingObjective;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InsightsAgentTest {
  private static final UUID EXECUTION_ID = UUID.randomUUID();

  @Test
  void derivesStructuredInsightOnlyFromRecordedToolResults() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    EventRepository.SessionSummary session =
        new EventRepository.SessionSummary("session-1", "Miami", null, "anon-1", Instant.now());
    AgentToolInvocation sessions =
        new AgentToolInvocation(
            UUID.randomUUID(), "searchEvents", "result:sessions", "SUCCEEDED", List.of(session));
    AgentToolInvocation events =
        new AgentToolInvocation(
            UUID.randomUUID(),
            "searchEvents",
            "result:events",
            "SUCCEEDED",
            List.of(event("BOOKING_COMPLETED")));
    when(tools.invoke(eq("searchEvents"), any(), eq(EXECUTION_ID)))
        .thenReturn(sessions)
        .thenReturn(events);

    MarketingInsight insight =
        new InsightsAgent(tools).derive(objective(), EXECUTION_ID, "correlation-1");

    assertThat(insight).isNotNull();
    assertThat(insight.metrics())
        .containsEntry("observedSessions", 1)
        .containsEntry("bookingCompletedSessions", 1L);
    assertThat(insight.evidenceRefs()).containsExactly("result:sessions", "result:events");
    assertThat(insight.correlationId()).isEqualTo("correlation-1");
  }

  @Test
  void producesNoInsightWhenEvidenceContainsNoCompletedBooking() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    EventRepository.SessionSummary session =
        new EventRepository.SessionSummary("session-1", "Miami", null, "anon-1", Instant.now());
    when(tools.invoke(eq("searchEvents"), any(), eq(EXECUTION_ID)))
        .thenReturn(
            new AgentToolInvocation(
                UUID.randomUUID(),
                "searchEvents",
                "result:sessions",
                "SUCCEEDED",
                List.of(session)))
        .thenReturn(
            new AgentToolInvocation(
                UUID.randomUUID(),
                "searchEvents",
                "result:events",
                "SUCCEEDED",
                List.of(event("DESTINATION_SEARCHED"))));

    assertThat(new InsightsAgent(tools).derive(objective(), EXECUTION_ID, "correlation-2"))
        .isNull();
  }

  private EventEnvelope event(String name) {
    return new EventEnvelope(
        UUID.randomUUID(),
        name,
        Instant.now(),
        Instant.now(),
        "1.0",
        "test",
        "session-1",
        "anon-1",
        null,
        UUID.randomUUID().toString(),
        new EventEnvelope.Consent(true, true),
        Map.of());
  }

  private MarketingObjective objective() {
    return new MarketingObjective(
        "weekend-booking",
        "Increase weekend leisure booking conversion",
        "Increase conversion",
        "Increase completed leisure bookings",
        "BOOKING_COMPLETED",
        BigDecimal.TEN,
        "Weekend leisure guests",
        Map.of(),
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 3, 31),
        MarketingObjective.Status.ACTIVE,
        "test",
        Instant.now());
  }
}
