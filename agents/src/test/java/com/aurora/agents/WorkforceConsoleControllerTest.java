package com.aurora.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurora.experiments.ActivationAttemptRepository;
import com.aurora.experiments.ExperimentAnalysisService;
import com.aurora.experiments.ExperimentProposal;
import com.aurora.experiments.ExperimentProposalService;
import com.aurora.experiments.UnknownExperimentException;
import com.aurora.objectives.MarketingObjective;
import com.aurora.objectives.MarketingObjectiveService;
import com.aurora.objectives.WorkflowStageTimingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkforceConsoleControllerTest {
  @Test
  void preActivationProposalDoesNotBlankTheWorkforceView() {
    MarketingObjectiveService objectives = mock(MarketingObjectiveService.class);
    MarketingInsightRepository insights = mock(MarketingInsightRepository.class);
    ExperimentProposalService proposals = mock(ExperimentProposalService.class);
    ExperimentAnalysisService analyses = mock(ExperimentAnalysisService.class);
    AgentExecutionRepository executions = mock(AgentExecutionRepository.class);
    WorkflowStageTimingService timings = mock(WorkflowStageTimingService.class);
    ActivationAttemptRepository attempts = mock(ActivationAttemptRepository.class);
    MarketingObjective objective = objective("objective");
    ExperimentProposal proposal = proposal("missing-definition");

    when(objectives.list()).thenReturn(List.of(objective));
    when(insights.findAll()).thenReturn(List.of());
    when(proposals.list()).thenReturn(List.of(proposal));
    when(proposals.audit(proposal.proposalId())).thenReturn(List.of());
    when(proposals.activationAttempts(proposal.proposalId())).thenReturn(List.of());
    when(executions.findAll()).thenReturn(List.of());
    when(timings.findByObjectiveId("objective")).thenReturn(List.of());
    when(attempts.findAll()).thenReturn(List.of());
    when(analyses.list("missing-definition"))
        .thenThrow(new UnknownExperimentException("missing-definition", List.of()));

    WorkforceConsoleController.WorkforceView view =
        new WorkforceConsoleController(
                objectives, insights, proposals, analyses, executions, timings, attempts)
            .workforce();

    assertThat(view.objectives()).hasSize(1);
    assertThat(view.objectives().get(0).proposals()).hasSize(1);
    assertThat(view.objectives().get(0).proposals().get(0).analyses()).isEmpty();
    assertThat(view.objectives().get(0).proposals().get(0).analysisError())
        .isEqualTo("No activated experiment definition exists yet.");
  }

  private MarketingObjective objective(String id) {
    return new MarketingObjective(
        id,
        "Objective",
        "Description",
        "Business goal",
        "BOOKING_COMPLETED",
        BigDecimal.TEN,
        "travelers",
        Map.of(),
        LocalDate.now().minusDays(1),
        LocalDate.now().plusDays(30),
        MarketingObjective.Status.ACTIVE,
        "tester",
        Instant.now());
  }

  private ExperimentProposal proposal(String experimentId) {
    return new ExperimentProposal(
        UUID.randomUUID(),
        "objective",
        UUID.randomUUID(),
        experimentId,
        "Experiment",
        "Description",
        "travelers",
        "signal",
        "Test the experience",
        List.of(
            new ExperimentProposal.Variant("control", 50),
            new ExperimentProposal.Variant("treatment", 50)),
        "BOOKING_COMPLETED",
        30,
        BigDecimal.ONE,
        "Reasoning",
        List.of("evidence"),
        "correlation",
        ExperimentProposal.GovernanceState.PROPOSED,
        Instant.now());
  }
}
