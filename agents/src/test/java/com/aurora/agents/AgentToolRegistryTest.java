package com.aurora.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aurora.context.ContextService;
import com.aurora.decision.DecisionPolicy;
import com.aurora.experiments.ExperimentService;
import com.aurora.ingest.EventRepository;
import com.aurora.models.ModelService;
import com.aurora.signals.SignalEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AgentToolRegistryTest {
  @Test
  void exposesOnlyReadToolsAndNoProposalOrActivationWrites() {
    AgentToolRegistry registry =
        new AgentToolRegistry(
            new ObjectMapper(),
            mock(AgentToolInvocationRepository.class),
            mock(EventRepository.class),
            mock(ContextService.class),
            mock(SignalEngine.class),
            mock(ModelService.class),
            mock(DecisionPolicy.class),
            mock(ExperimentService.class));

    assertThat(registry.toolNames())
        .containsExactlyInAnyOrder(
            "searchEvents",
            "getCustomerContext",
            "listSignals",
            "getSignalDefinition",
            "calculateSignal",
            "listModels",
            "evaluateModel",
            "evaluateDecision",
            "listExperiments",
            "getExperimentPerformance",
            "getExperimentExposures",
            "getExperimentOutcomes");
    assertThat(registry.toolNames())
        .doesNotContain(
            "createExperimentProposal",
            "deployModel",
            "rollbackModel",
            "transitionSignal",
            "activateExperiment");
  }

  @Test
  void persistsEveryToolInvocationWithEvidenceReference() {
    AgentToolInvocationRepository invocations = mock(AgentToolInvocationRepository.class);
    AgentToolRegistry registry =
        new AgentToolRegistry(
            new ObjectMapper(),
            invocations,
            mock(EventRepository.class),
            mock(ContextService.class),
            mock(SignalEngine.class),
            mock(ModelService.class),
            mock(DecisionPolicy.class),
            mock(ExperimentService.class));

    registry.invoke("listSignals", AgentToolInputs.Empty.INSTANCE);

    verify(invocations)
        .save(
            any(),
            any(),
            eq("listSignals"),
            any(),
            startsWith("agent-tool-result:"),
            any(),
            eq("SUCCEEDED"),
            any(),
            any());
  }
}
