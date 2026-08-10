package com.aurora.agents;

import com.aurora.common.SignalDefinition;
import com.aurora.experiments.ExperimentProposal;
import com.aurora.ingest.EventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ExperimentationAgent {
  private static final double TWO_SIDED_ALPHA_Z = 1.96;
  private static final double POWER_80_Z = 0.84;
  private static final double PLATFORM_MINIMUM_EXPOSURES = 30;
  private final AgentToolProvider tools;

  public ExperimentationAgent(AgentToolProvider tools) {
    this.tools = tools;
  }

  public AgentResult<ExperimentProposal> propose(
      ExperimentationInput input, UUID executionId, String correlationId) {
    MarketingInsight insight = input.insight();
    Object signalMetric = insight.metrics().get("signalName");
    if (!(signalMetric instanceof String signalName) || signalName.isBlank()) {
      return AgentResult.refused(
          "NO_USABLE_SIGNAL", "The insight did not identify a usable targeting signal");
    }
    AgentToolInvocation sessionsCall =
        tools.invoke("listSessions", AgentToolInputs.Empty.INSTANCE, executionId);
    List<EventRepository.SessionSummary> sessions =
        sessionsCall.resultAsList(EventRepository.SessionSummary.class);
    if (sessions.isEmpty()) {
      return AgentResult.refused("NO_SESSIONS", "No sessions were available for the objective");
    }
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
    if (sessions.isEmpty()) {
      return AgentResult.refused(
          "NO_SESSIONS_IN_OBJECTIVE_WINDOW", "No sessions fell within the objective window");
    }
    AgentToolInvocation signalsCall =
        tools.invoke("listSignals", AgentToolInputs.Empty.INSTANCE, executionId);
    boolean signalExists =
        signalsCall.resultAsList(SignalDefinition.class).stream()
            .anyMatch(definition -> definition.name().equals(signalName));
    if (!signalExists) {
      return AgentResult.refused(
          "SIGNAL_NOT_REGISTERED",
          "The insight signal was not present in the registered signal framework");
    }
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
    if (withSignal.isEmpty() || withoutSignal.isEmpty()) {
      return AgentResult.refused(
          "NO_COMPARABLE_SIGNAL_GROUPS",
          "Evidence did not contain sessions on both sides of the signal comparison");
    }
    long convertedWith =
        withSignal.stream().filter(AgentToolResults.SignalObservation::converted).count();
    long convertedWithout =
        withoutSignal.stream().filter(AgentToolResults.SignalObservation::converted).count();
    if (convertedWith + convertedWithout == 0) {
      return AgentResult.refused(
          "NO_CONVERSIONS", "Evidence contained no completed target-KPI conversions");
    }

    double withRate = (double) convertedWith / withSignal.size();
    double withoutRate = (double) convertedWithout / withoutSignal.size();
    double expectedEffect = Math.abs(withRate - withoutRate);
    if (expectedEffect <= 0d) {
      return AgentResult.refused(
          "ZERO_OBSERVED_EFFECT", "Observed conversion rates did not support an expected effect");
    }
    double projectedRate = Math.min(1d, withoutRate + expectedEffect);
    double pooledRate = (withoutRate + projectedRate) / 2d;
    double standardError =
        TWO_SIDED_ALPHA_Z * Math.sqrt(2d * pooledRate * (1d - pooledRate))
            + POWER_80_Z
                * Math.sqrt(
                    withoutRate * (1d - withoutRate) + projectedRate * (1d - projectedRate));
    int derivedMinimum =
        (int) Math.ceil((standardError * standardError) / (expectedEffect * expectedEffect));
    int minimumExposures = Math.max((int) PLATFORM_MINIMUM_EXPOSURES, derivedMinimum);
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate elapsedEnd =
        today.isBefore(input.objective().endDate()) ? today : input.objective().endDate();
    long elapsedWindowDays =
        Math.max(1, ChronoUnit.DAYS.between(input.objective().startDate(), elapsedEnd) + 1);
    double sessionsPerDay = observations.size() / (double) elapsedWindowDays;
    long remainingDays =
        Math.max(0, ChronoUnit.DAYS.between(today, input.objective().endDate()) + 1);
    double projectedSessions = sessionsPerDay * remainingDays;
    if (projectedSessions < minimumExposures * 2d) {
      return AgentResult.refused(
          "INSUFFICIENT_PROJECTED_TRAFFIC",
          "Observed traffic cannot fill both randomized arms before the objective ends",
          java.util.Map.of(
              "minimumExposuresPerVariant", minimumExposures,
              "observedSessions", observations.size(),
              "elapsedWindowDays", elapsedWindowDays,
              "observedSessionsPerDay", sessionsPerDay,
              "remainingDays", remainingDays,
              "projectedSessions", projectedSessions));
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
    return AgentResult.success(
        new ExperimentProposal(
            UUID.randomUUID(),
            input.objective().objectiveId(),
            insight.insightId(),
            experimentId,
            "Test personalized "
                + actionSlug
                + " experience for "
                + input.objective().targetAudience(),
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
                new ExperimentProposal.Variant(
                    "control-existing-" + actionSlug + "-experience", 50),
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
                + "from the observed baseline conversion rate "
                + formatRate(withoutRate)
                + " and expected effect "
                + formatRate(expectedEffect)
                + " using a conventional two-sided 5% significance level and 80% power, then floored at the platform minimum of 30 per arm. "
                + "Feasibility projects the observed "
                + formatRate(sessionsPerDay)
                + " sessions per day across the elapsed "
                + elapsedWindowDays
                + "-day portion of the objective window over the "
                + remainingDays
                + " remaining days.",
            List.copyOf(evidence),
            correlationId,
            ExperimentProposal.GovernanceState.PROPOSED,
            Instant.now()));
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

  private String formatRate(double value) {
    return "%.4f".formatted(value);
  }
}
