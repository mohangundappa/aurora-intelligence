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
import org.junit.jupiter.api.Test;

class ExperimentationAgentTest {
  @Test
  void derivesProposalFromObservedSignalEvidence() {
    AgentToolRegistry tools = mock(AgentToolRegistry.class);
    UUID executionId = UUID.randomUUID();
    when(tools.invoke("listSessions", AgentToolInputs.Empty.INSTANCE, executionId))
        .thenReturn(
            invocation(
                "listSessions",
                List.of(
                    new EventRepository.SessionSummary("s1", null, null, null, Instant.now()),
                    new EventRepository.SessionSummary("s2", null, null, null, Instant.now()),
                    new EventRepository.SessionSummary("s3", null, null, null, Instant.now()))));
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
                "weekend-getaway-affinity", "BOOKING_COMPLETED", List.of("s1", "s2", "s3")),
            executionId))
        .thenReturn(
            invocation(
                "calculateSignal",
                List.of(
                    new AgentToolResults.SignalObservation("s1", true, 1, true),
                    new AgentToolResults.SignalObservation("s2", false, 0, false),
                    new AgentToolResults.SignalObservation("s3", false, 0, true))));

    ExperimentProposal proposal =
        new ExperimentationAgent(tools).propose(input(), executionId, "correlation");

    assertThat(proposal).isNotNull();
    assertThat(proposal.variants())
        .extracting(ExperimentProposal.Variant::allocationPercentage)
        .containsExactly(33, 67);
    assertThat(proposal.expectedEffect()).isEqualByComparingTo(BigDecimal.valueOf(0.5));
    assertThat(proposal.hypothesis()).contains("test whether");
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
                    "correlation"))
        .isNull();
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
