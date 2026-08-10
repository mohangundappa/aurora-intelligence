package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurora.objectives.WorkflowStageTimingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentProposalServiceTest {
  private final ExperimentProposalRepository repository = mock(ExperimentProposalRepository.class);
  private final ExperimentDefinitionService definitions = mock(ExperimentDefinitionService.class);
  private final WorkflowStageTimingService timings = mock(WorkflowStageTimingService.class);
  private final ExperimentProposalService service =
      new ExperimentProposalService(repository, definitions, timings);

  @Test
  void approvalRequiresIdentityAndReason() {
    ExperimentProposal proposal = proposal(ExperimentProposal.GovernanceState.PROPOSED);
    when(repository.findById(proposal.proposalId())).thenReturn(java.util.Optional.of(proposal));

    assertThatThrownBy(() -> service.approve(proposal.proposalId(), " ", "reason"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("approver identity");
    assertThatThrownBy(() -> service.approve(proposal.proposalId(), "approver", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("transition reason");
  }

  @Test
  void illegalTransitionsAreRefused() {
    ExperimentProposal proposal = proposal(ExperimentProposal.GovernanceState.REJECTED);
    when(repository.findById(proposal.proposalId())).thenReturn(java.util.Optional.of(proposal));

    assertThatThrownBy(() -> service.approve(proposal.proposalId(), "approver", "not applicable"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.activate(proposal.proposalId(), "operator", "retry"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectionRequiresReasonAndAuditsTheHumanActor() {
    ExperimentProposal proposal = proposal(ExperimentProposal.GovernanceState.PROPOSED);
    when(repository.findById(proposal.proposalId()))
        .thenReturn(java.util.Optional.of(proposal), java.util.Optional.of(proposal));

    service.reject(proposal.proposalId(), "reviewer", "insufficient evidence");

    verify(repository)
        .transition(
            proposal.proposalId(),
            ExperimentProposal.GovernanceState.PROPOSED,
            ExperimentProposal.GovernanceState.REJECTED,
            "reviewer",
            "insufficient evidence");
  }

  @Test
  void activationRequiresApprovalAndWritesDraftDefinition() {
    UUID id = UUID.randomUUID();
    ExperimentProposal approved = proposal(ExperimentProposal.GovernanceState.APPROVED, id);
    ExperimentProposal activated = proposal(ExperimentProposal.GovernanceState.ACTIVATED, id);
    when(repository.findById(id))
        .thenReturn(java.util.Optional.of(approved), java.util.Optional.of(activated));

    service.activate(id, "operator", "approved rollout configuration");

    verify(definitions).saveAfterCommit(approved.toDraftDefinition());
    verify(repository)
        .transition(
            id,
            ExperimentProposal.GovernanceState.APPROVED,
            ExperimentProposal.GovernanceState.ACTIVATED,
            "operator",
            "approved rollout configuration");
  }

  @Test
  void activationFailureLeavesApprovalIntactAndReportsPersistenceState() {
    UUID id = UUID.randomUUID();
    ExperimentProposal approved = proposal(ExperimentProposal.GovernanceState.APPROVED, id);
    when(repository.findById(id)).thenReturn(java.util.Optional.of(approved));
    doThrow(
            new IllegalStateException(
                "Experiment definition was persisted but is not yet in the serving view"))
        .when(definitions)
        .saveAfterCommit(approved.toDraftDefinition());

    assertThatThrownBy(() -> service.activate(id, "operator", "activate"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("persisted but is not yet in the serving view");
    verify(repository, never())
        .transition(
            id,
            ExperimentProposal.GovernanceState.APPROVED,
            ExperimentProposal.GovernanceState.ACTIVATED,
            "operator",
            "activate");
  }

  private ExperimentProposal proposal(ExperimentProposal.GovernanceState state) {
    return proposal(state, UUID.randomUUID());
  }

  private ExperimentProposal proposal(ExperimentProposal.GovernanceState state, UUID id) {
    return new ExperimentProposal(
        id,
        "objective",
        UUID.randomUUID(),
        "experiment-" + id,
        "Experiment",
        "Description",
        "Weekend leisure travelers",
        "weekend-getaway-affinity",
        "Hypothesis",
        List.of(
            new ExperimentProposal.Variant("treatment", 20),
            new ExperimentProposal.Variant("control", 80)),
        "BOOKING_COMPLETED",
        30,
        BigDecimal.valueOf(0.1),
        "Reasoning",
        List.of("evidence"),
        "correlation",
        state,
        Instant.now());
  }
}
