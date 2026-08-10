package com.aurora.agents;

import com.aurora.common.SignalDefinition;
import com.aurora.ingest.EventRepository;
import com.aurora.objectives.MarketingObjective;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InsightsAgent {
  private static final int PLATFORM_MINIMUM_COMPARABLE_GROUP_SIZE = 30;
  private final AgentToolProvider tools;

  public InsightsAgent(AgentToolProvider tools) {
    this.tools = tools;
  }

  public AgentResult<MarketingInsight> derive(
      MarketingObjective objective, UUID executionId, String correlationId) {
    AgentToolInvocation sessionsCall =
        tools.invoke("listSessions", AgentToolInputs.Empty.INSTANCE, executionId);
    List<EventRepository.SessionSummary> sessions =
        sessionsCall.resultAsList(EventRepository.SessionSummary.class);
    if (sessions.isEmpty()) {
      return AgentResult.refused("NO_SESSIONS", "No sessions were available for the objective");
    }

    AgentToolInvocation signalsCall =
        tools.invoke("listSignals", AgentToolInputs.Empty.INSTANCE, executionId);
    List<SignalDefinition> definitions = signalsCall.resultAsList(SignalDefinition.class);
    SignalDefinition relevantSignal = selectRelevantSignal(definitions, objective);
    if (relevantSignal == null) {
      return AgentResult.refused(
          "NO_RELEVANT_SIGNAL", "No registered signal matched the objective audience or goal");
    }

    AgentToolInvocation calculationCall =
        tools.invoke(
            "calculateSignal",
            new AgentToolInputs.SignalCalculation(
                relevantSignal.name(),
                objective.targetKpi(),
                sessions.stream().map(session -> session.sessionId()).toList()),
            executionId);
    List<AgentToolResults.SignalObservation> observations =
        calculationCall.resultAsList(AgentToolResults.SignalObservation.class);
    List<AgentToolResults.SignalObservation> withSignal =
        observations.stream().filter(AgentToolResults.SignalObservation::signalPresent).toList();
    List<AgentToolResults.SignalObservation> withoutSignal =
        observations.stream().filter(observation -> !observation.signalPresent()).toList();
    if (withSignal.isEmpty() || withoutSignal.isEmpty()) {
      return AgentResult.refused(
          "NO_COMPARABLE_SIGNAL_GROUPS",
          "Evidence did not contain sessions on both sides of the signal comparison");
    }

    long convertedWithSignal =
        withSignal.stream().filter(AgentToolResults.SignalObservation::converted).count();
    long convertedWithoutSignal =
        withoutSignal.stream().filter(AgentToolResults.SignalObservation::converted).count();
    if (convertedWithSignal + convertedWithoutSignal == 0) {
      return AgentResult.refused(
          "NO_CONVERSIONS", "Evidence contained no completed target-KPI conversions");
    }

    double withSignalRate = (double) convertedWithSignal / withSignal.size();
    double withoutSignalRate = (double) convertedWithoutSignal / withoutSignal.size();
    String direction =
        withSignalRate > withoutSignalRate
            ? "higher"
            : withSignalRate < withoutSignalRate ? "lower" : "the same";

    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("observedSessions", sessions.size());
    metrics.put("signalName", relevantSignal.name());
    metrics.put("targetKpi", objective.targetKpi());
    metrics.put("targetAudience", objective.targetAudience());
    metrics.put("sessionsWithSignal", withSignal.size());
    metrics.put("sessionsWithoutSignal", withoutSignal.size());
    metrics.put("conversionsWithSignal", convertedWithSignal);
    metrics.put("conversionsWithoutSignal", convertedWithoutSignal);
    boolean ratesComparable =
        withSignal.size() >= PLATFORM_MINIMUM_COMPARABLE_GROUP_SIZE
            && withoutSignal.size() >= PLATFORM_MINIMUM_COMPARABLE_GROUP_SIZE;
    if (ratesComparable) {
      metrics.put("conversionRateWithSignal", withSignalRate);
      metrics.put("conversionRateWithoutSignal", withoutSignalRate);
      metrics.put("conversionRateDifference", withSignalRate - withoutSignalRate);
    } else {
      metrics.put("comparisonRatesWithheld", true);
      metrics.put("minimumComparableGroupSize", PLATFORM_MINIMUM_COMPARABLE_GROUP_SIZE);
    }
    String finding =
        ratesComparable
            ? "In the observed data, sessions with "
                + relevantSignal.name()
                + " showed "
                + direction
                + " "
                + objective.targetKpi()
                + " conversion than sessions without it ("
                + formatPercent(withSignalRate)
                + " vs "
                + formatPercent(withoutSignalRate)
                + "); this observed association should be tested by an experiment."
            : "In the observed data, sessions with "
                + relevantSignal.name()
                + " showed "
                + direction
                + " "
                + objective.targetKpi()
                + " conversion than sessions without it; comparative rates are "
                + "withheld because at least one comparison group has fewer than "
                + PLATFORM_MINIMUM_COMPARABLE_GROUP_SIZE
                + " sessions. This observed association should be tested by an experiment.";
    return AgentResult.success(
        new MarketingInsight(
            UUID.randomUUID(),
            objective.objectiveId(),
            relevantSignal.name() + " conversion comparison",
            finding,
            metrics,
            List.of(
                sessionsCall.resultReference(),
                signalsCall.resultReference(),
                calculationCall.resultReference()),
            correlationId,
            Instant.now()));
  }

  private SignalDefinition selectRelevantSignal(
      List<SignalDefinition> definitions, MarketingObjective objective) {
    Set<String> objectiveTerms =
        Stream.of(
                objective.name(),
                objective.description(),
                objective.businessGoal(),
                objective.targetAudience())
            .filter(value -> value != null)
            .flatMap(value -> Arrays.stream(value.toLowerCase().split("[^a-z0-9]+")))
            .filter(term -> !term.isBlank())
            .collect(Collectors.toSet());
    return definitions.stream()
        .max(
            Comparator.comparingLong(
                definition ->
                    Arrays.stream(definition.name().toLowerCase().split("-"))
                        .filter(objectiveTerms::contains)
                        .count()))
        .filter(
            definition ->
                Arrays.stream(definition.name().toLowerCase().split("-"))
                    .anyMatch(objectiveTerms::contains))
        .orElse(null);
  }

  private String formatPercent(double rate) {
    return "%.1f%%".formatted(rate * 100);
  }
}
