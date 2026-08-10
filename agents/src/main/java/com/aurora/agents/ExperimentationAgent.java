package com.aurora.agents;

import com.aurora.common.SignalDefinition;
import com.aurora.experiments.ExperimentProposal;
import com.aurora.ingest.EventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
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
    Instant windowStart = input.objective().startDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant windowEnd =
        input.objective().endDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    sessions =
        sessions.stream()
            .filter(
                session ->
                    session.lastActivity() != null
                        && !session.lastActivity().isBefore(windowStart)
                        && session.lastActivity().isBefore(windowEnd))
            .toList();
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
    double expectedEffect = Math.abs(withRate - withoutRate);
    if (expectedEffect <= 0d) return null;
    double projectedRate = Math.min(1d, withoutRate + expectedEffect);
    double variance = withoutRate * (1d - withoutRate) + projectedRate * (1d - projectedRate);
    int derivedMinimum = (int) Math.ceil(4d * variance / (expectedEffect * expectedEffect));
    int minimumExposures = Math.max(30, derivedMinimum);
    if (minimumExposures * 2 > observations.size()) {
      throw new IllegalArgumentException(
          "Observed evidence cannot support "
              + minimumExposures
              + " exposures per randomized arm within the objective window; refusing to propose");
    }
    Set<String> evidence = new LinkedHashSet<>(insight.evidenceRefs());
    evidence.add(sessionsCall.resultReference());
    evidence.add(signalsCall.resultReference());
    evidence.add(calculationCall.resultReference());
    String direction =
        withRate > withoutRate ? "higher" : withRate < withoutRate ? "lower" : "the same";
    String experimentId =
        humanReadableExperimentId(
            input.objective().name(), signalName, input.objective().objectiveId());
    String actionSlug = slug(input.objective().targetKpi());
    return new ExperimentProposal(
        UUID.randomUUID(),
        input.objective().objectiveId(),
        insight.insightId(),
        experimentId,
        "Test personalized " + actionSlug + " experience for " + input.objective().targetAudience(),
        "Experiment proposed from observed signal evidence for " + input.objective().name(),
        input.objective().targetAudience(),
        signalName,
        "Sessions carrying "
            + signalName
            + " showed "
            + direction
            + " "
            + input.objective().targetKpi()
            + " conversion; test whether that association replicates under randomized assignment.",
        List.of(
            new ExperimentProposal.Variant("control-existing-" + actionSlug + "-experience", 50),
            new ExperimentProposal.Variant("personalized-" + actionSlug + "-experience", 50)),
        input.objective().targetKpi(),
        minimumExposures,
        BigDecimal.valueOf(expectedEffect).setScale(6, RoundingMode.HALF_UP),
        "The audience is "
            + input.objective().targetAudience()
            + " targeted by "
            + signalName
            + "; the arms are randomized existing and personalized experiences rather than signal-defined segments. "
            + "A 50/50 split is used to preserve comparable randomized arms. The sample threshold is derived "
            + "from the observed baseline conversion rate and expected effect, then floored at the platform minimum of 30 per arm.",
        List.copyOf(evidence),
        correlationId,
        ExperimentProposal.GovernanceState.PROPOSED,
        Instant.now());
  }

  private String humanReadableExperimentId(
      String objectiveName, String signalName, String objectiveId) {
    String slug = slug(objectiveName + "-" + signalName);
    String suffix =
        UUID.nameUUIDFromBytes((objectiveId + ":" + signalName).getBytes(StandardCharsets.UTF_8))
            .toString()
            .substring(0, 8);
    return slug + "-" + suffix;
  }

  private String slug(String value) {
    return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }
}
