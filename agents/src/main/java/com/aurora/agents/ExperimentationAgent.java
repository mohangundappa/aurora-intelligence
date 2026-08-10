package com.aurora.agents;

import com.aurora.common.SignalDefinition;
import com.aurora.experiments.ExperimentProposal;
import com.aurora.ingest.EventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ExperimentationAgent {
  private final AgentToolRegistry tools;

  public ExperimentationAgent(AgentToolRegistry tools) {
    this.tools = tools;
  }

  public ExperimentProposal propose(
      ExperimentationInput input, UUID executionId, String correlationId) {
    MarketingInsight insight = input.insight();
    Object signalMetric = insight.metrics().get("signalName");
    if (!(signalMetric instanceof String signalName) || signalName.isBlank()) return null;
    AgentToolInvocation sessionsCall =
        tools.invoke("listSessions", AgentToolInputs.Empty.INSTANCE, executionId);
    List<EventRepository.SessionSummary> sessions =
        sessionsCall.resultAsList(EventRepository.SessionSummary.class);
    if (sessions.isEmpty()) return null;
    AgentToolInvocation signalsCall =
        tools.invoke("listSignals", AgentToolInputs.Empty.INSTANCE, executionId);
    boolean signalExists =
        signalsCall.resultAsList(SignalDefinition.class).stream()
            .anyMatch(definition -> definition.name().equals(signalName));
    if (!signalExists) return null;
    AgentToolInvocation calculationCall =
        tools.invoke(
            "calculateSignal",
            new AgentToolInputs.SignalCalculation(
                signalName,
                input.objective().targetKpi(),
                sessions.stream().map(EventRepository.SessionSummary::sessionId).toList()),
            executionId);
    List<AgentToolResults.SignalObservation> observations =
        calculationCall.resultAsList(AgentToolResults.SignalObservation.class);
    List<AgentToolResults.SignalObservation> withSignal =
        observations.stream().filter(AgentToolResults.SignalObservation::signalPresent).toList();
    List<AgentToolResults.SignalObservation> withoutSignal =
        observations.stream().filter(observation -> !observation.signalPresent()).toList();
    if (withSignal.isEmpty() || withoutSignal.isEmpty()) return null;
    long convertedWith =
        withSignal.stream().filter(AgentToolResults.SignalObservation::converted).count();
    long convertedWithout =
        withoutSignal.stream().filter(AgentToolResults.SignalObservation::converted).count();
    if (convertedWith + convertedWithout == 0) return null;

    double withRate = (double) convertedWith / withSignal.size();
    double withoutRate = (double) convertedWithout / withoutSignal.size();
    int withAllocation =
        Math.max(1, Math.min(99, (int) Math.round(100d * withSignal.size() / observations.size())));
    int withoutAllocation = 100 - withAllocation;
    int minimumExposures = Math.max(1, Math.min(withSignal.size(), withoutSignal.size()));
    Set<String> evidence = new LinkedHashSet<>(insight.evidenceRefs());
    evidence.add(sessionsCall.resultReference());
    evidence.add(signalsCall.resultReference());
    evidence.add(calculationCall.resultReference());
    String direction =
        withRate > withoutRate ? "higher" : withRate < withoutRate ? "lower" : "the same";
    return new ExperimentProposal(
        UUID.randomUUID(),
        input.objective().objectiveId(),
        insight.insightId(),
        "proposal-" + UUID.randomUUID(),
        "Test " + signalName + " association",
        "Experiment proposed from observed signal evidence for " + input.objective().name(),
        "Sessions carrying "
            + signalName
            + " showed "
            + direction
            + " "
            + input.objective().targetKpi()
            + " conversion; test whether that association replicates under randomized assignment.",
        List.of(
            new ExperimentProposal.Variant(signalName + "-present", withAllocation),
            new ExperimentProposal.Variant(signalName + "-absent", withoutAllocation)),
        input.objective().targetKpi(),
        minimumExposures,
        BigDecimal.valueOf(Math.abs(withRate - withoutRate)).setScale(6, RoundingMode.HALF_UP),
        "The proposed allocation and sample threshold are derived from the observed signal-group sizes; "
            + "the expected effect is the observed conversion-rate difference.",
        List.copyOf(evidence),
        correlationId,
        ExperimentProposal.GovernanceState.PROPOSED,
        Instant.now());
  }
}
