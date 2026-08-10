package com.aurora.agents;

import com.aurora.context.ContextService;
import com.aurora.decision.DecisionPolicy;
import com.aurora.experiments.ExperimentService;
import com.aurora.ingest.EventRepository;
import com.aurora.models.ModelService;
import com.aurora.signals.SignalEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class AgentToolRegistry {
  private final ObjectMapper mapper;
  private final AgentToolInvocationRepository invocations;
  private final Map<String, AgentTool<?, ?>> tools;

  public AgentToolRegistry(
      ObjectMapper mapper,
      AgentToolInvocationRepository invocations,
      EventRepository events,
      ContextService context,
      SignalEngine signals,
      ModelService models,
      DecisionPolicy decisions,
      ExperimentService experiments) {
    this.mapper = mapper;
    this.invocations = invocations;
    this.tools = buildTools(events, context, signals, models, decisions, experiments);
  }

  public List<String> toolNames() {
    return List.copyOf(tools.keySet());
  }

  public AgentToolInvocation invoke(String name, Object arguments) {
    AgentTool<?, ?> tool =
        Optional.ofNullable(tools.get(name))
            .orElseThrow(() -> new IllegalArgumentException("Unknown agent tool " + name));
    Object typedArguments = mapper.convertValue(arguments, tool.inputType());
    Instant startedAt = Instant.now();
    UUID callId = UUID.randomUUID();
    Object result;
    String status;
    RuntimeException failure = null;
    try {
      result = execute(tool, typedArguments);
      status = "SUCCEEDED";
    } catch (RuntimeException exception) {
      failure = exception;
      result =
          Map.of(
              "error",
              exception.getMessage() == null
                  ? exception.getClass().getName()
                  : exception.getMessage());
      status = "FAILED";
    }
    Instant completedAt = Instant.now();
    String resultReference = "agent-tool-result:" + callId;
    invocations.save(
        callId, name, typedArguments, resultReference, result, status, startedAt, completedAt);
    if ("FAILED".equals(status)) {
      throw failure;
    }
    return new AgentToolInvocation(callId, name, resultReference, status, result);
  }

  private Map<String, AgentTool<?, ?>> buildTools(
      EventRepository events,
      ContextService context,
      SignalEngine signals,
      ModelService models,
      DecisionPolicy decisions,
      ExperimentService experiments) {
    Map<String, AgentTool<?, ?>> registered = new LinkedHashMap<>();
    registered.put(
        "searchEvents",
        tool(
            "searchEvents",
            AgentToolInputs.Session.class,
            input -> events.findBySession(input.sessionId())));
    registered.put(
        "getCustomerContext",
        tool(
            "getCustomerContext",
            AgentToolInputs.Session.class,
            input -> context.forSessionReadOnly(input.sessionId())));
    registered.put(
        "listSignals",
        tool("listSignals", AgentToolInputs.Empty.class, ignored -> signals.registryDefinitions()));
    registered.put(
        "getSignalDefinition",
        tool(
            "getSignalDefinition",
            AgentToolInputs.Signal.class,
            input -> signals.definition(input.signalName())));
    registered.put(
        "calculateSignal",
        tool(
            "calculateSignal",
            AgentToolInputs.SignalCalculation.class,
            input ->
                signals.calculateAllReadOnly(input.sessionId()).stream()
                    .filter(
                        signal ->
                            input.signalName() == null || signal.name().equals(input.signalName()))
                    .toList()));
    registered.put(
        "listModels",
        tool(
            "listModels",
            AgentToolInputs.Model.class,
            input -> models.versions(input.modelName())));
    registered.put(
        "evaluateModel",
        tool(
            "evaluateModel",
            AgentToolInputs.ModelEvaluation.class,
            input -> models.evaluate(input.modelName(), input.version())));
    registered.put(
        "evaluateDecision",
        tool(
            "evaluateDecision",
            AgentToolInputs.Decision.class,
            input -> decisions.evaluate(input.signals())));
    registered.put(
        "listExperiments",
        tool("listExperiments", AgentToolInputs.Empty.class, ignored -> experiments.definitions()));
    registered.put(
        "getExperimentPerformance",
        tool(
            "getExperimentPerformance",
            AgentToolInputs.Experiment.class,
            input -> experiments.performance(input.experimentId())));
    registered.put(
        "getExperimentExposures",
        tool(
            "getExperimentExposures",
            AgentToolInputs.Experiment.class,
            input -> experiments.exposures(input.experimentId())));
    registered.put(
        "getExperimentOutcomes",
        tool(
            "getExperimentOutcomes",
            AgentToolInputs.Experiment.class,
            input -> experiments.outcomes(input.experimentId())));
    return Map.copyOf(registered);
  }

  private <I, O> AgentTool<I, O> tool(String name, Class<I> inputType, Function<I, O> action) {
    return new AgentTool<>() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public Class<I> inputType() {
        return inputType;
      }

      @Override
      public O execute(I input) {
        return action.apply(input);
      }
    };
  }

  @SuppressWarnings("unchecked")
  private Object execute(AgentTool<?, ?> tool, Object arguments) {
    return ((AgentTool<Object, ?>) tool).execute(arguments);
  }
}
