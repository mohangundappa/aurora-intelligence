package com.aurora.agents;

import com.aurora.common.EventEnvelope;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class AgentToolRegistry implements AgentToolProvider {
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

  @Override
  public Set<String> readOnlyToolNames() {
    return tools.values().stream()
        .filter(AgentTool::readOnly)
        .map(AgentTool::name)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  public AgentToolInvocation invoke(String name, Object arguments) {
    return invoke(name, arguments, null);
  }

  public AgentToolInvocation invoke(String name, Object arguments, UUID executionId) {
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
        callId,
        executionId,
        name,
        typedArguments,
        resultReference,
        result,
        status,
        startedAt,
        completedAt);
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
        "listSessions",
        tool("listSessions", AgentToolInputs.Empty.class, ignored -> events.recentSessions()));
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
            input -> calculateSignalAcrossSessions(input, events, signals)));
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

  private List<AgentToolResults.SignalObservation> calculateSignalAcrossSessions(
      AgentToolInputs.SignalCalculation input, EventRepository events, SignalEngine signals) {
    return input.sessionIds().stream()
        .map(
            sessionId -> {
              List<EventEnvelope> sessionEvents = events.findBySession(sessionId);
              boolean converted =
                  sessionEvents.stream()
                      .anyMatch(event -> input.conversionEvent().equals(event.eventName()));
              return signals.calculateAllReadOnly(sessionId).stream()
                  .filter(signal -> signal.name().equals(input.signalName()))
                  .findFirst()
                  .map(
                      signal ->
                          new AgentToolResults.SignalObservation(
                              sessionId, signal.value() > 0, signal.value(), converted))
                  .orElseGet(
                      () -> new AgentToolResults.SignalObservation(sessionId, false, 0, converted));
            })
        .toList();
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
      public boolean readOnly() {
        return true;
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
