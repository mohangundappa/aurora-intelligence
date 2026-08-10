package com.aurora.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurora.common.SignalDefinition;
import com.aurora.experiments.ExperimentProposal;
import com.aurora.ingest.EventRepository;
import com.aurora.objectives.MarketingObjective;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ExperimentationAgentTest {
  @Test
  void derivesProposalFromObservedSignalEvidence() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    UUID executionId = UUID.randomUUID();
    List<EventRepository.SessionSummary> sessions =
        IntStream.range(0, 100)
            .mapToObj(
                index ->
                    new EventRepository.SessionSummary(
                        "s" + index, null, null, null, Instant.now()))
            .toList();
    List<String> sessionIds =
        sessions.stream().map(EventRepository.SessionSummary::sessionId).toList();
    when(tools.invoke("listSessions", AgentToolInputs.Empty.INSTANCE, executionId))
        .thenReturn(invocation("listSessions", sessions));
    when(tools.invoke("listSignals", AgentToolInputs.Empty.INSTANCE, executionId))
        .thenReturn(
            invocation(
                "listSignals",
                List.of(
                    new SignalDefinition(
                        "weekend-getaway-affinity",
                        "1",
                        List.of("DESTINATION_SEARCHED"),
                        SignalDefinition.CalculationType.AGGREGATION,
                        "real-time",
                        "30d",
                        "0-100",
                        "standard",
                        "15m",
                        "24h",
                        false,
                        "test",
                        SignalDefinition.LifecycleStatus.DEPLOYED,
                        "test"))));
    when(tools.invoke(
            "calculateSignal",
            new AgentToolInputs.SignalCalculation(
                "weekend-getaway-affinity", "BOOKING_COMPLETED", sessionIds),
            executionId))
        .thenReturn(
            invocation(
                "calculateSignal",
                IntStream.range(0, 100)
                    .mapToObj(
                        index ->
                            new AgentToolResults.SignalObservation(
                                "s" + index,
                                index < 50,
                                index < 50 ? 1 : 0,
                                (index < 50 || index >= 75)))
                    .toList()));

    ExperimentProposal proposal =
        new ExperimentationAgent(tools).propose(input(), executionId, "correlation").output();

    assertThat(proposal).isNotNull();
    assertThat(proposal.variants())
        .extracting(ExperimentProposal.Variant::allocationPercentage)
        .containsExactly(50, 50);
    assertThat(proposal.expectedEffect()).isEqualByComparingTo(BigDecimal.valueOf(0.5));
    assertThat(proposal.hypothesis()).contains("test whether");
    assertThat(proposal.minimumExposuresPerVariant()).isGreaterThanOrEqualTo(30);
    assertThat(proposal.targetAudience()).isEqualTo("weekend leisure travelers");
    assertThat(proposal.targetingSignal()).isEqualTo("weekend-getaway-affinity");
    assertThat(proposal.experimentId()).contains("weekend-leisure-booking-conversion");
    assertThat(proposal.reasoning())
        .contains("5% significance", "80% power", "0.5000", "0.5000", "sessions per day");

    ExperimentProposal repeated =
        new ExperimentationAgent(tools).propose(input(), executionId, "correlation-2").output();
    assertThat(repeated.experimentId()).isEqualTo(proposal.experimentId());
  }

  @Test
  void refusesWhenInsightDoesNotCarryAUsableSignal() {
    MarketingInsight insight =
        new MarketingInsight(
            UUID.randomUUID(),
            "objective",
            "subject",
            "finding",
            Map.of("targetKpi", "BOOKING_COMPLETED"),
            List.of("evidence"),
            "correlation",
            Instant.now());

    assertThat(
            new ExperimentationAgent(mock(AgentToolRegistry.class))
                .propose(
                    new ExperimentationInput(input().objective(), insight),
                    UUID.randomUUID(),
                    "correlation")
                .refusal())
        .extracting(AgentRefusal::code)
        .isEqualTo("NO_USABLE_SIGNAL");
  }

  @Test
  void withholdsRateBasedPlanningInputsWhenComparisonGroupsAreTooSmall() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    UUID executionId = UUID.randomUUID();
    List<EventRepository.SessionSummary> sessions =
        IntStream.range(0, 50)
            .mapToObj(
                index ->
                    new EventRepository.SessionSummary(
                        "s" + index, null, null, null, Instant.now()))
            .toList();
    List<String> sessionIds =
        sessions.stream().map(EventRepository.SessionSummary::sessionId).toList();
    when(tools.invoke("listSessions", AgentToolInputs.Empty.INSTANCE, executionId))
        .thenReturn(invocation("listSessions", sessions));
    when(tools.invoke("listSignals", AgentToolInputs.Empty.INSTANCE, executionId))
        .thenReturn(invocation("listSignals", List.of(signal())));
    when(tools.invoke(
            "calculateSignal",
            new AgentToolInputs.SignalCalculation(
                "weekend-getaway-affinity", "BOOKING_COMPLETED", sessionIds),
            executionId))
        .thenReturn(
            invocation(
                "calculateSignal",
                IntStream.range(0, 50)
                    .mapToObj(
                        index ->
                            new AgentToolResults.SignalObservation(
                                "s" + index, index < 25, index < 25 ? 1 : 0, index < 25))
                    .toList()));

    AgentResult<ExperimentProposal> result =
        new ExperimentationAgent(tools)
            .propose(
                new ExperimentationInput(input().objective(), input().insight()),
                executionId,
                "correlation");

    assertThat(result.refusal()).isNull();
    assertThat(result.output().expectedEffect()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.output().minimumExposuresPerVariant()).isEqualTo(30);
    assertThat(result.output().reasoning())
        .contains("observed baseline conversion rate and expected effect are withheld")
        .doesNotContain("1.0000");
  }

  @Test
  void refusesWhenProjectedTrafficCannotFillTheRandomizedArms() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    UUID executionId = UUID.randomUUID();
    List<EventRepository.SessionSummary> sessions =
        IntStream.range(0, 100)
            .mapToObj(
                index ->
                    new EventRepository.SessionSummary(
                        "s" + index, null, null, null, Instant.now()))
            .toList();
    List<String> sessionIds =
        sessions.stream().map(EventRepository.SessionSummary::sessionId).toList();
    when(tools.invoke("listSessions", AgentToolInputs.Empty.INSTANCE, executionId))
        .thenReturn(invocation("listSessions", sessions));
    when(tools.invoke("listSignals", AgentToolInputs.Empty.INSTANCE, executionId))
        .thenReturn(invocation("listSignals", List.of(signal())));
    when(tools.invoke(
            "calculateSignal",
            new AgentToolInputs.SignalCalculation(
                "weekend-getaway-affinity", "BOOKING_COMPLETED", sessionIds),
            executionId))
        .thenReturn(
            invocation(
                "calculateSignal",
                IntStream.range(0, 100)
                    .mapToObj(
                        index ->
                            new AgentToolResults.SignalObservation(
                                "s" + index,
                                index < 50,
                                index < 50 ? 1 : 0,
                                (index < 30 || (index >= 50 && index < 75))))
                    .toList()));

    AgentResult<ExperimentProposal> result =
        new ExperimentationAgent(tools)
            .propose(
                new ExperimentationInput(
                    new MarketingObjective(
                        "objective",
                        "Increase weekend leisure booking conversion",
                        "Description",
                        "Increase conversion",
                        "BOOKING_COMPLETED",
                        BigDecimal.valueOf(0.5),
                        "weekend leisure travelers",
                        Map.of(),
                        LocalDate.now(),
                        LocalDate.now(),
                        MarketingObjective.Status.ACTIVE,
                        "analyst",
                        Instant.now()),
                    input().insight()),
                executionId,
                "correlation");

    assertThat(result.refusal())
        .extracting(AgentRefusal::code)
        .isEqualTo("INSUFFICIENT_PROJECTED_TRAFFIC");
    assertThat(result.refusal().details()).containsKeys("observedSessionsPerDay", "remainingDays");
  }

  @Test
  void projectsTrafficUsingElapsedWindowRatherThanFutureObjectiveDays() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    UUID executionId = UUID.randomUUID();
    List<EventRepository.SessionSummary> sessions =
        IntStream.range(0, 60)
            .mapToObj(
                index ->
                    new EventRepository.SessionSummary(
                        "s" + index, null, null, null, Instant.now()))
            .toList();
    List<String> sessionIds =
        sessions.stream().map(EventRepository.SessionSummary::sessionId).toList();
    when(tools.invoke("listSessions", AgentToolInputs.Empty.INSTANCE, executionId))
        .thenReturn(invocation("listSessions", sessions));
    when(tools.invoke("listSignals", AgentToolInputs.Empty.INSTANCE, executionId))
        .thenReturn(invocation("listSignals", List.of(signal())));
    when(tools.invoke(
            "calculateSignal",
            new AgentToolInputs.SignalCalculation(
                "weekend-getaway-affinity", "BOOKING_COMPLETED", sessionIds),
            executionId))
        .thenReturn(
            invocation(
                "calculateSignal",
                IntStream.range(0, 60)
                    .mapToObj(
                        index ->
                            new AgentToolResults.SignalObservation(
                                "s" + index,
                                index < 30,
                                index < 20 ? 1 : 0,
                                index < 20 || (index >= 30 && index < 35)))
                    .toList()));

    MarketingObjective objective =
        new MarketingObjective(
            "objective",
            "Increase weekend leisure booking conversion",
            "Description",
            "Increase conversion",
            "BOOKING_COMPLETED",
            BigDecimal.valueOf(0.5),
            "weekend leisure travelers",
            Map.of(),
            LocalDate.now().minusDays(10),
            LocalDate.now().plusDays(70),
            MarketingObjective.Status.ACTIVE,
            "analyst",
            Instant.now());
    AgentResult<ExperimentProposal> result =
        new ExperimentationAgent(tools)
            .propose(
                new ExperimentationInput(objective, input().insight()), executionId, "correlation");

    assertThat(result.refusal()).isNull();
    assertThat(result.output()).isNotNull();
    assertThat(result.output().reasoning()).contains("elapsed", "5.5 sessions per day");
  }

  private SignalDefinition signal() {
    return new SignalDefinition(
        "weekend-getaway-affinity",
        "1",
        List.of("DESTINATION_SEARCHED"),
        SignalDefinition.CalculationType.AGGREGATION,
        "real-time",
        "30d",
        "0-100",
        "standard",
        "15m",
        "24h",
        false,
        "test",
        SignalDefinition.LifecycleStatus.DEPLOYED,
        "test");
  }

  private ExperimentationInput input() {
    return new ExperimentationInput(
        new MarketingObjective(
            "objective",
            "Increase weekend leisure booking conversion",
            "Description",
            "Increase conversion",
            "BOOKING_COMPLETED",
            BigDecimal.valueOf(0.5),
            "weekend leisure travelers",
            Map.of(),
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            MarketingObjective.Status.ACTIVE,
            "analyst",
            Instant.now()),
        new MarketingInsight(
            UUID.randomUUID(),
            "objective",
            "subject",
            "finding",
            Map.of("signalName", "weekend-getaway-affinity", "targetKpi", "BOOKING_COMPLETED"),
            List.of("insight-evidence"),
            "correlation",
            Instant.now()));
  }

  private AgentToolInvocation invocation(String tool, Object result) {
    return new AgentToolInvocation(
        UUID.randomUUID(), tool, "evidence:" + tool, "SUCCEEDED", result);
  }
}
