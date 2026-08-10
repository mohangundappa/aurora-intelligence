package com.aurora.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurora.common.SignalDefinition;
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
  private static final SignalDefinition WEEKEND_SIGNAL =
      new SignalDefinition(
          "weekend-getaway-affinity",
          "1.0",
          List.of("TRAVEL_DATES_SELECTED"),
          SignalDefinition.CalculationType.RULE,
          "real-time",
          "30d",
          "0-100",
          "event-derived",
          "30m",
          "24h",
          true,
          "Weekend dates matched the getaway pattern.",
          SignalDefinition.LifecycleStatus.DRAFT,
          "Customer Intelligence");

  @Test
  void derivesSignalGroundedComparisonFromRecordedToolResults() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    stubSessions(tools);
    stubSignal(tools, observations(true, false));

    MarketingInsight insight =
        new InsightsAgent(tools).derive(objective(), EXECUTION_ID, "correlation-1").output();

    assertThat(insight).isNotNull();
    assertThat(insight.finding())
        .contains(
            "observed data",
            "higher",
            "comparative rates are withheld",
            "association",
            "tested by an experiment");
    assertThat(insight.metrics())
        .containsEntry("signalName", "weekend-getaway-affinity")
        .containsEntry("targetKpi", "BOOKING_COMPLETED")
        .containsEntry("targetAudience", "Weekend leisure guests")
        .containsEntry("sessionsWithSignal", 1)
        .containsEntry("sessionsWithoutSignal", 1)
        .containsEntry("conversionsWithSignal", 1L)
        .containsEntry("conversionsWithoutSignal", 0L)
        .containsEntry("comparisonRatesWithheld", true)
        .doesNotContainKeys(
            "conversionRateWithSignal", "conversionRateWithoutSignal", "conversionRateDifference");
    assertThat(insight.evidenceRefs())
        .containsExactly("result:sessions", "result:signals", "result:calculation");
    assertThat(insight.correlationId()).isEqualTo("correlation-1");
  }

  @Test
  void findingChangesWhenTheObservedConversionGroupsChange() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    stubSessions(tools);
    when(tools.invoke(eq("calculateSignal"), any(), eq(EXECUTION_ID)))
        .thenReturn(
            invocation("calculateSignal", "result:calculation-1", observations(true, false)))
        .thenReturn(
            invocation("calculateSignal", "result:calculation-2", observations(false, true)));
    when(tools.invoke(eq("listSignals"), any(), eq(EXECUTION_ID)))
        .thenReturn(invocation("listSignals", "result:signals", List.of(WEEKEND_SIGNAL)));

    InsightsAgent agent = new InsightsAgent(tools);
    MarketingInsight first = agent.derive(objective(), EXECUTION_ID, "correlation-1").output();
    MarketingInsight second = agent.derive(objective(), EXECUTION_ID, "correlation-2").output();

    assertThat(first.finding()).contains("higher", "association", "tested by an experiment");
    assertThat(second.finding()).contains("lower", "association", "tested by an experiment");
    assertThat(first.finding()).isNotEqualTo(second.finding());
  }

  @Test
  void producesNoInsightWithoutTwoComparableSignalGroups() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    stubSessions(tools);
    stubSignal(tools, List.of(observation("session-1", true, true)));

    assertThat(
            new InsightsAgent(tools).derive(objective(), EXECUTION_ID, "correlation-2").refusal())
        .extracting(AgentRefusal::code)
        .isEqualTo("NO_COMPARABLE_SIGNAL_GROUPS");
  }

  @Test
  void producesNoInsightWhenComparableGroupsHaveNoConversions() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    stubSessions(tools);
    stubSignal(tools, observations(false, false));

    assertThat(
            new InsightsAgent(tools).derive(objective(), EXECUTION_ID, "correlation-3").refusal())
        .extracting(AgentRefusal::code)
        .isEqualTo("NO_CONVERSIONS");
  }

  private void stubSessions(AgentToolRegistry tools) {
    when(tools.invoke(eq("listSessions"), any(), eq(EXECUTION_ID)))
        .thenReturn(
            invocation(
                "listSessions",
                "result:sessions",
                List.of(
                    new EventRepository.SessionSummary(
                        "session-1", "Miami", null, "anon-1", Instant.now()),
                    new EventRepository.SessionSummary(
                        "session-2", "Miami", null, "anon-2", Instant.now()))));
  }

  private void stubSignal(
      AgentToolRegistry tools, List<AgentToolResults.SignalObservation> observations) {
    when(tools.invoke(eq("listSignals"), any(), eq(EXECUTION_ID)))
        .thenReturn(invocation("listSignals", "result:signals", List.of(WEEKEND_SIGNAL)));
    when(tools.invoke(eq("calculateSignal"), any(), eq(EXECUTION_ID)))
        .thenReturn(invocation("calculateSignal", "result:calculation", observations));
  }

  private List<AgentToolResults.SignalObservation> observations(
      boolean withSignalConverted, boolean withoutSignalConverted) {
    return List.of(
        observation("session-1", true, withSignalConverted),
        observation("session-2", false, withoutSignalConverted));
  }

  private AgentToolResults.SignalObservation observation(
      String sessionId, boolean signalPresent, boolean converted) {
    return new AgentToolResults.SignalObservation(
        sessionId, signalPresent, signalPresent ? 70 : 0, converted);
  }

  private AgentToolInvocation invocation(String toolName, String reference, Object result) {
    return new AgentToolInvocation(UUID.randomUUID(), toolName, reference, "SUCCEEDED", result);
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
