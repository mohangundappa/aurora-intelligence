package com.aurora.agents;

import com.aurora.experiments.ExperimentAnalysis;
import com.aurora.experiments.ExperimentPerformance;
import com.aurora.experiments.ExperimentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AnalyticsAgent {
  private static final int PLATFORM_MINIMUM_EXPOSURES = 30;
  private static final double TWO_SIDED_ALPHA_Z = 1.96;
  private static final double EIGHTY_PERCENT_POWER_Z = 0.84;

  private final AgentToolRegistry tools;

  public AnalyticsAgent(AgentToolRegistry tools) {
    this.tools = tools;
  }

  public AgentResult<ExperimentAnalysis> analyze(
      AnalyticsInput input, UUID executionId, String correlationId) {
    AgentToolInvocation performanceCall =
        tools.invoke(
            "getExperimentPerformance",
            new AgentToolInputs.Experiment(input.experimentId()),
            executionId);
    ExperimentPerformance performance = performanceCall.resultAs(ExperimentPerformance.class);
    AgentToolInvocation exposuresCall =
        tools.invoke(
            "getExperimentExposures",
            new AgentToolInputs.Experiment(input.experimentId()),
            executionId);
    List<ExperimentService.Exposure> exposures =
        exposuresCall.resultAsList(ExperimentService.Exposure.class);
    AgentToolInvocation outcomesCall =
        tools.invoke(
            "getExperimentOutcomes",
            new AgentToolInputs.Experiment(input.experimentId()),
            executionId);
    List<ExperimentService.Outcome> outcomes =
        outcomesCall.resultAsList(ExperimentService.Outcome.class);

    if (performance.variants().size() > 2) {
      return refusal(
          "MULTI_ARM_UNSUPPORTED",
          "This analysis supports two-arm comparisons only; multi-arm analysis requires multiple-comparison handling that is not implemented",
          performanceCall,
          exposuresCall,
          outcomesCall);
    }

    int minimumExposures =
        Math.max(PLATFORM_MINIMUM_EXPOSURES, performance.minimumExposuresPerVariant());
    Map<String, String> correlationVariants = new LinkedHashMap<>();
    exposures.forEach(
        exposure -> correlationVariants.put(exposure.correlationId(), exposure.variant()));
    Set<String> convertedCorrelations = new HashSet<>();
    outcomes.forEach(
        outcome -> {
          if (performance.primaryOutcomeEvent().equals(outcome.eventName())) {
            convertedCorrelations.add(outcome.correlationId());
          }
        });
    Map<String, Integer> outcomesByVariant = new LinkedHashMap<>();
    convertedCorrelations.forEach(
        outcomeCorrelationId -> {
          String variant = correlationVariants.get(outcomeCorrelationId);
          if (variant != null) outcomesByVariant.merge(variant, 1, Integer::sum);
        });

    List<ExperimentAnalysis.VariantResult> variants = new ArrayList<>();
    for (ExperimentPerformance.Variant variant : performance.variants()) {
      int exposed =
          (int)
              exposures.stream()
                  .filter(exposure -> variant.name().equals(exposure.variant()))
                  .count();
      int converted = outcomesByVariant.getOrDefault(variant.name(), 0);
      if (exposed == 0) {
        return refusal(
            "ZERO_EXPOSURES",
            "At least one experiment arm has zero exposures; no conclusion can be drawn",
            performanceCall,
            exposuresCall,
            outcomesCall);
      }
      if (exposed < minimumExposures) {
        return refusal(
            "INSUFFICIENT_SAMPLE",
            "At least one experiment arm is below the platform sample guard; keep running before drawing a conclusion",
            performanceCall,
            exposuresCall,
            outcomesCall);
      }
      variants.add(
          new ExperimentAnalysis.VariantResult(
              variant.name(), exposed, converted, (double) converted / exposed));
    }
    if (variants.size() < 2) {
      return refusal(
          "INSUFFICIENT_VARIANTS",
          "The experiment does not contain two comparable arms",
          performanceCall,
          exposuresCall,
          outcomesCall);
    }

    ExperimentAnalysis.VariantResult control = control(variants);
    ExperimentAnalysis.VariantResult treatment = treatment(variants, control);
    boolean namedControl = control.variant().toLowerCase().contains("control");
    String baselineReason =
        namedControl
            ? "The baseline arm was selected because its declared name contains 'control'."
            : "No arm was named 'control', so the first declared arm "
                + control.variant()
                + " was selected as the baseline.";
    double absoluteLift = treatment.conversionRate() - control.conversionRate();
    if (control.conversionRate() == 0d && absoluteLift != 0d) {
      return refusal(
          "RELATIVE_LIFT_UNDEFINED",
          "The control conversion rate is zero, so relative lift is not defined",
          performanceCall,
          exposuresCall,
          outcomesCall);
    }
    double relativeLift =
        control.conversionRate() == 0d ? 0d : absoluteLift / control.conversionRate();
    double pooled =
        (treatment.outcomes() + control.outcomes())
            / (double) (treatment.exposures() + control.exposures());
    double standardError =
        Math.sqrt(pooled * (1d - pooled) * (1d / treatment.exposures() + 1d / control.exposures()));
    double zScore = standardError == 0d ? 0d : Math.abs(absoluteLift) / standardError;
    double minimumDetectableEffect =
        (TWO_SIDED_ALPHA_Z + EIGHTY_PERCENT_POWER_Z)
            * Math.sqrt(
                control.conversionRate()
                    * (1d - control.conversionRate())
                    * (1d / treatment.exposures() + 1d / control.exposures()));
    boolean significant = zScore >= TWO_SIDED_ALPHA_Z;
    ExperimentAnalysis.Recommendation recommendation =
        significant
            ? absoluteLift > 0
                ? ExperimentAnalysis.Recommendation.SHIP
                : absoluteLift < 0
                    ? ExperimentAnalysis.Recommendation.STOP
                    : ExperimentAnalysis.Recommendation.ITERATE
            : ExperimentAnalysis.Recommendation.ITERATE;
    String reasoning =
        "Observed in this experiment, the "
            + treatment.variant()
            + " arm converted at "
            + formatPercent(treatment.conversionRate())
            + " versus "
            + formatPercent(control.conversionRate())
            + " for "
            + control.variant()
            + ", an absolute difference of "
            + formatPercent(absoluteLift)
            + " and relative difference of "
            + formatPercent(relativeLift)
            + ". Both arms met the "
            + minimumExposures
            + "-exposure guard. "
            + baselineReason
            + " The comparison uses a two-sided 5% significance threshold; the observed z-score was "
            + formatDecimal(zScore)
            + ". Based on the baseline rate and the exposures actually observed in both arms, the approximate minimum detectable effect at 80% power is "
            + formatPercent(minimumDetectableEffect)
            + " (percentage points). "
            + (significant
                ? "This randomized comparison supports a directional conclusion at that significance threshold."
                : "The observed difference did not meet the significance threshold, so the result does not support a directional conclusion yet.")
            + " This analysis evaluates only the primary outcome; no guardrail metrics were examined, so the recommendation does not establish guardrail safety."
            + " Recommendation: "
            + recommendation
            + ".";
    return AgentResult.success(
        new ExperimentAnalysis(
            UUID.randomUUID(),
            input.experimentId(),
            variants,
            true,
            BigDecimal.valueOf(absoluteLift).setScale(6, RoundingMode.HALF_UP),
            BigDecimal.valueOf(relativeLift).setScale(6, RoundingMode.HALF_UP),
            recommendation,
            reasoning,
            List.of(
                performanceCall.resultReference(),
                exposuresCall.resultReference(),
                outcomesCall.resultReference()),
            correlationId,
            Instant.now()));
  }

  private AgentResult<ExperimentAnalysis> refusal(
      String code,
      String reason,
      AgentToolInvocation performance,
      AgentToolInvocation exposures,
      AgentToolInvocation outcomes) {
    return new AgentResult<>(
        null,
        new AgentRefusal(
            code,
            reason,
            Map.of(
                "evidenceRefs",
                List.of(
                    performance.resultReference(),
                    exposures.resultReference(),
                    outcomes.resultReference()))));
  }

  private ExperimentAnalysis.VariantResult control(
      List<ExperimentAnalysis.VariantResult> variants) {
    return variants.stream()
        .filter(variant -> variant.variant().toLowerCase().contains("control"))
        .findFirst()
        .orElse(variants.get(0));
  }

  private ExperimentAnalysis.VariantResult treatment(
      List<ExperimentAnalysis.VariantResult> variants, ExperimentAnalysis.VariantResult control) {
    return variants.stream()
        .filter(variant -> variant != control)
        .findFirst()
        .orElse(variants.get(1));
  }

  private String formatPercent(double value) {
    return "%.1f%%".formatted(value * 100);
  }

  private String formatDecimal(double value) {
    return "%.3f".formatted(value);
  }
}
