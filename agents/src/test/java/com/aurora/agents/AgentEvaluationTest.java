package com.aurora.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurora.agents.evaluation.AgentEvaluationDataset;
import com.aurora.agents.evaluation.AgentEvaluationHarness;
import com.aurora.agents.evaluation.AgentEvaluationReport;
import com.aurora.agents.evaluation.AgentEvaluationRun;
import com.aurora.agents.evaluation.AgentEvaluationScenario;
import com.aurora.common.SignalDefinition;
import com.aurora.experiments.ExperimentAnalysis;
import com.aurora.experiments.ExperimentPerformance;
import com.aurora.experiments.ExperimentProposal;
import com.aurora.experiments.ExperimentService;
import com.aurora.ingest.EventRepository;
import com.aurora.objectives.MarketingObjective;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentEvaluationTest {
  private static final UUID EXECUTION_ID = UUID.randomUUID();
  private static final String SIGNAL = "destination-intent";
  private static final String PRIMARY_OUTCOME = "BOOKING_COMPLETED";

  @Test
  void evaluationDatasetPassesAgainstDeterministicRuntime() {
    List<AgentEvaluationScenario> scenarios = AgentEvaluationDataset.load(new ObjectMapper());

    AgentEvaluationReport report =
        AgentEvaluationHarness.run(
            scenarios, this::runDeterministicRuntime, new EvaluationTools("").readOnlyToolNames());

    assertThat(report.summary()).isNotBlank();
    assertThat(report.failed()).withFailMessage(report::summary).isZero();
    assertThat(report.passed()).isEqualTo(scenarios.size());
  }

  @Test
  void causalLanguageTripwireFailsForKnownOverclaimingPhrase() {
    AgentEvaluationScenario scenario =
        AgentEvaluationDataset.load(new ObjectMapper()).stream()
            .filter(candidate -> candidate.id().equals("analytics-causal-language-tripwire"))
            .findFirst()
            .orElseThrow();

    AgentEvaluationReport report =
        AgentEvaluationHarness.run(
            List.of(scenario),
            ignored ->
                new AgentEvaluationRun(
                    new Object(),
                    null,
                    List.of(),
                    List.of("fixture:evidence"),
                    "The treatment drives more bookings.",
                    true,
                    false,
                    "ITERATE",
                    null),
            new EvaluationTools("").readOnlyToolNames());

    assertThat(report.failed()).isEqualTo(1);
    assertThat(report.summary()).contains("client text must not imply causation");
  }

  @Test
  void emptyOrUnspecifiedRefusalAssertionsFailLoudly() {
    AgentEvaluationScenario empty =
        new AgentEvaluationScenario("empty", "ANALYTICS", "fixture", null, List.of(), Map.of());
    AgentEvaluationScenario missingCode =
        new AgentEvaluationScenario(
            "missing-code", "ANALYTICS", "fixture", null, List.of("EXPECTED_REFUSAL"), Map.of());

    AgentEvaluationReport report =
        AgentEvaluationHarness.run(
            List.of(empty, missingCode),
            ignored ->
                new AgentEvaluationRun(
                    null,
                    AgentResult.refused("SOME_CODE", "reason").refusal(),
                    List.of(),
                    List.of(),
                    null,
                    false,
                    false,
                    null,
                    null),
            new EvaluationTools("").readOnlyToolNames());

    assertThat(report.failed()).isEqualTo(2);
    assertThat(report.summary())
        .contains("scenario must declare at least one obligation")
        .contains("refusal scenario must declare and return expected refusal code");
  }

  private AgentEvaluationRun runDeterministicRuntime(AgentEvaluationScenario scenario) {
    EvaluationTools tools = new EvaluationTools(scenario.fixture());
    return switch (scenario.agent()) {
      case "INSIGHTS" -> insightsRun(tools, scenario.fixture());
      case "EXPERIMENTATION" -> experimentationRun(tools, scenario.fixture());
      case "ANALYTICS" -> analyticsRun(tools, scenario.fixture());
      default -> throw new IllegalArgumentException("Unknown evaluation agent " + scenario.agent());
    };
  }

  private AgentEvaluationRun insightsRun(EvaluationTools tools, String fixture) {
    AgentResult<MarketingInsight> result =
        new InsightsAgent(tools).derive(objective(), EXECUTION_ID, "evaluation");
    return new AgentEvaluationRun(
        result.output(),
        result.refusal(),
        tools.calls,
        result.output() == null ? List.of() : result.output().evidenceRefs(),
        result.output() == null ? null : result.output().finding(),
        true,
        false,
        null,
        null);
  }

  private AgentEvaluationRun experimentationRun(EvaluationTools tools, String fixture) {
    MarketingInsight insight = insightFixture();
    AgentResult<ExperimentProposal> result =
        new ExperimentationAgent(tools)
            .propose(
                new ExperimentationInput(objectiveForExperiment(fixture), insight),
                EXECUTION_ID,
                "evaluation");
    return new AgentEvaluationRun(
        result.output(),
        result.refusal(),
        tools.calls,
        result.output() == null ? List.of() : result.output().evidenceRefs(),
        result.output() == null ? null : result.output().reasoning(),
        true,
        false,
        null,
        result.output() == null ? null : result.output().minimumExposuresPerVariant());
  }

  private MarketingObjective objectiveForExperiment(String fixture) {
    if (!"experimentation-insufficient-projected-traffic".equals(fixture)) {
      return objective();
    }
    return new MarketingObjective(
        "evaluation-objective",
        "Destination conversion",
        "Improve destination booking conversion",
        "Increase destination booking conversion",
        PRIMARY_OUTCOME,
        BigDecimal.ONE,
        "destination travelers",
        Map.of(),
        LocalDate.now(ZoneOffset.UTC),
        LocalDate.now(ZoneOffset.UTC),
        MarketingObjective.Status.ACTIVE,
        "evaluation",
        Instant.now());
  }

  private AgentEvaluationRun analyticsRun(EvaluationTools tools, String fixture) {
    AgentResult<ExperimentAnalysis> result =
        new AnalyticsAgent(tools)
            .analyze(
                new AnalyticsInput("evaluation-objective", "evaluation-experiment"),
                EXECUTION_ID,
                "evaluation");
    return new AgentEvaluationRun(
        result.output(),
        result.refusal(),
        tools.calls,
        result.output() == null ? List.of() : result.output().evidenceRefs(),
        result.output() == null ? null : result.output().reasoning(),
        result.output() != null && result.output().sufficientSample(),
        result.output() != null
            && (result.output().absoluteLift() != null || result.output().relativeLift() != null),
        result.output() == null ? null : result.output().recommendation().name(),
        null);
  }

  private MarketingObjective objective() {
    return new MarketingObjective(
        "evaluation-objective",
        "Destination conversion",
        "Improve destination booking conversion",
        "Increase destination booking conversion",
        PRIMARY_OUTCOME,
        BigDecimal.ONE,
        "destination travelers",
        Map.of(),
        LocalDate.now(ZoneOffset.UTC),
        LocalDate.now(ZoneOffset.UTC).plusDays(30),
        MarketingObjective.Status.ACTIVE,
        "evaluation",
        Instant.now());
  }

  private MarketingInsight insightFixture() {
    return new MarketingInsight(
        UUID.nameUUIDFromBytes("evaluation-insight".getBytes()),
        "evaluation-objective",
        "Destination association",
        "In the observed data, sessions with destination-intent showed higher booking conversion than sessions without it; this observed association should be tested by an experiment.",
        Map.of(
            "signalName",
            SIGNAL,
            "targetKpi",
            PRIMARY_OUTCOME,
            "targetAudience",
            "destination travelers",
            "conversionRateWithSignal",
            0.5,
            "conversionRateWithoutSignal",
            0.1),
        List.of("fixture:insight"),
        "evaluation",
        Instant.now());
  }

  private final class EvaluationTools implements AgentToolProvider {
    private final String fixture;
    private final List<String> calls = new ArrayList<>();

    private EvaluationTools(String fixture) {
      this.fixture = fixture;
    }

    @Override
    public List<String> toolNames() {
      return List.of(
          "listSessions",
          "listSignals",
          "calculateSignal",
          "getExperimentPerformance",
          "getExperimentExposures",
          "getExperimentOutcomes");
    }

    @Override
    public java.util.Set<String> readOnlyToolNames() {
      return java.util.Set.copyOf(toolNames());
    }

    @Override
    public AgentToolInvocation invoke(String name, Object arguments, UUID executionId) {
      calls.add(name);
      return new AgentToolInvocation(
          UUID.randomUUID(), name, "fixture:" + name, "SUCCEEDED", result(name));
    }

    private Object result(String name) {
      return switch (name) {
        case "listSessions" -> sessions();
        case "listSignals" -> signals();
        case "calculateSignal" -> observations();
        case "getExperimentPerformance" -> performance();
        case "getExperimentExposures" -> exposures();
        case "getExperimentOutcomes" -> outcomes();
        default -> throw new IllegalArgumentException("Unexpected tool " + name);
      };
    }

    private List<EventRepository.SessionSummary> sessions() {
      if ("insights-no-sessions".equals(fixture)) return List.of();
      int count =
          fixture.contains("insufficient-projected") ? 2 : fixture.contains("grounded") ? 100 : 2;
      return java.util.stream.IntStream.range(0, count)
          .mapToObj(
              index ->
                  new EventRepository.SessionSummary(
                      "session-" + index,
                      "destination",
                      "customer-" + index,
                      "anonymous-" + index,
                      Instant.now()))
          .toList();
    }

    private List<SignalDefinition> signals() {
      if ("insights-no-matching-signal".equals(fixture)) return List.of();
      return List.of(
          new SignalDefinition(
              SIGNAL,
              "1",
              List.of("destination"),
              SignalDefinition.CalculationType.RULE,
              "derived",
              "30d",
              "0..1",
              "high",
              "daily",
              "30d",
              false,
              "destination intent",
              SignalDefinition.LifecycleStatus.DEPLOYED,
              "evaluation"));
    }

    private List<AgentToolResults.SignalObservation> observations() {
      if ("insights-one-sided-comparison".equals(fixture)) {
        return List.of(
            new AgentToolResults.SignalObservation("session-0", true, 1, true),
            new AgentToolResults.SignalObservation("session-1", true, 1, false));
      }
      if ("insights-no-conversions".equals(fixture)) {
        return List.of(
            new AgentToolResults.SignalObservation("session-0", true, 1, false),
            new AgentToolResults.SignalObservation("session-1", false, 0, false));
      }
      if (fixture.startsWith("experimentation-")) {
        int count = "experimentation-insufficient-projected-traffic".equals(fixture) ? 2 : 100;
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(
                index ->
                    new AgentToolResults.SignalObservation(
                        "session-" + index, index % 2 == 0, 1, index % 2 == 0))
            .toList();
      }
      return List.of(
          new AgentToolResults.SignalObservation("session-0", true, 1, true),
          new AgentToolResults.SignalObservation("session-1", false, 0, false));
    }

    private ExperimentPerformance performance() {
      List<ExperimentPerformance.Variant> variants =
          switch (fixture) {
            case "analytics-zero-exposure-arm" ->
                List.of(variant("control", 0, 0), variant("treatment", 40, 20));
            case "analytics-subminimum-arm" ->
                List.of(variant("control", 29, 10), variant("treatment", 40, 20));
            case "analytics-multi-arm" ->
                List.of(
                    variant("arm-a", 40, 10), variant("arm-b", 40, 12), variant("arm-c", 40, 15));
            default -> List.of(variant("control", 40, 0), variant("treatment", 40, 20));
          };
      return new ExperimentPerformance(
          "evaluation-experiment",
          "Evaluation experiment",
          "Fixture experiment",
          PRIMARY_OUTCOME,
          30,
          variants,
          false,
          "");
    }

    private ExperimentPerformance.Variant variant(String name, int exposed, int outcomes) {
      return new ExperimentPerformance.Variant(
          name, exposed, 0, 0, outcomes, exposed == 0 ? 0 : (double) outcomes / exposed);
    }

    private List<ExperimentService.Exposure> exposures() {
      if ("analytics-zero-exposure-arm".equals(fixture)) {
        return exposureList("treatment", 40);
      }
      if ("analytics-subminimum-arm".equals(fixture)) {
        return java.util.stream.Stream.concat(
                exposureList("control", 29).stream(), exposureList("treatment", 40).stream())
            .toList();
      }
      if ("analytics-multi-arm".equals(fixture)) return List.of();
      return java.util.stream.Stream.concat(
              exposureList("control", 40).stream(), exposureList("treatment", 40).stream())
          .toList();
    }

    private List<ExperimentService.Exposure> exposureList(String variant, int count) {
      return java.util.stream.IntStream.range(0, count)
          .mapToObj(
              index ->
                  new ExperimentService.Exposure(
                      "evaluation-experiment",
                      variant,
                      "subject-" + variant + index,
                      "session-" + variant + index,
                      "corr-" + variant + index,
                      Instant.now()))
          .toList();
    }

    private List<ExperimentService.Outcome> outcomes() {
      if ("analytics-undefined-relative-lift".equals(fixture)) {
        return exposureList("treatment", 20).stream()
            .map(
                exposure ->
                    new ExperimentService.Outcome(
                        UUID.randomUUID(),
                        PRIMARY_OUTCOME,
                        exposure.correlationId(),
                        Instant.now()))
            .toList();
      }
      return List.of();
    }
  }
}
