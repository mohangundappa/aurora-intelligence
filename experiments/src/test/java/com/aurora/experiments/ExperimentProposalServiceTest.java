package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurora.common.martech.ActivationResult;
import com.aurora.common.martech.AudienceActivation;
import com.aurora.common.martech.CampaignRegistration;
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
    providerService().activate(id, "operator", "approved rollout configuration");

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
  void duplicateLogicalExperimentIsRejectedBeforeProviderActivation() {
    UUID id = UUID.randomUUID();
    ExperimentProposal approved = proposal(ExperimentProposal.GovernanceState.APPROVED, id);
    when(repository.findById(id)).thenReturn(java.util.Optional.of(approved));
    doThrow(
            new IllegalStateException(
                "Experiment definition id '"
                    + approved.experimentId()
                    + "' already describes a registered logical experiment"))
        .when(definitions)
        .assertCanRegister(approved.toDraftDefinition());

    assertThatThrownBy(() -> providerService().activate(id, "operator", "activate"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already describes a registered logical experiment");
    verify(definitions, never()).saveAfterCommit(approved.toDraftDefinition());
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

    assertThatThrownBy(() -> providerService().activate(id, "operator", "activate"))
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

  @Test
  void approvedActivationRegistersAudienceAndCampaignThroughProviderSeams() {
    AudienceActivation audiences = mock(AudienceActivation.class);
    CampaignRegistration campaigns = mock(CampaignRegistration.class);
    when(audiences.activate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(acceptedResult("audience"));
    when(campaigns.register(org.mockito.ArgumentMatchers.any()))
        .thenReturn(acceptedResult("campaign"));
    ExperimentProposalService providerService =
        new ExperimentProposalService(repository, definitions, timings, audiences, campaigns);
    UUID id = UUID.randomUUID();
    ExperimentProposal approved = proposal(ExperimentProposal.GovernanceState.APPROVED, id);
    when(repository.findById(id))
        .thenReturn(java.util.Optional.of(approved), java.util.Optional.of(approved));

    providerService.activate(id, "operator", "approved rollout configuration");

    verify(audiences).activate(org.mockito.ArgumentMatchers.any());
    verify(campaigns).register(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void partialAudienceActivationIsRecordedAndBlocksGovernanceTransition() {
    AudienceActivation audiences = mock(AudienceActivation.class);
    CampaignRegistration campaigns = mock(CampaignRegistration.class);
    ActivationAttemptRepository attempts = mock(ActivationAttemptRepository.class);
    when(audiences.activate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new ActivationResult(
                "audience",
                "key",
                ActivationResult.Status.PARTIAL,
                4,
                2,
                "two records rejected",
                java.util.Map.of("provider", "test")));
    ExperimentProposalService providerService =
        new ExperimentProposalService(
            repository, definitions, timings, audiences, campaigns, attempts);
    UUID id = UUID.randomUUID();
    ExperimentProposal approved = proposal(ExperimentProposal.GovernanceState.APPROVED, id);
    when(repository.findById(id)).thenReturn(java.util.Optional.of(approved));

    assertThatThrownBy(() -> providerService.activate(id, "operator", "activate"))
        .isInstanceOf(MarTechActivationException.class)
        .hasMessageContaining("PARTIAL")
        .hasMessageContaining("audience");
    verify(attempts).save(org.mockito.ArgumentMatchers.any(ActivationAttempt.class));
    verify(campaigns, never()).register(org.mockito.ArgumentMatchers.any());
    verify(repository, never())
        .transition(
            id,
            ExperimentProposal.GovernanceState.APPROVED,
            ExperimentProposal.GovernanceState.ACTIVATED,
            "operator",
            "activate");
  }

  @Test
  void missingProvidersAreNotRecordedAsAccepted() {
    UUID id = UUID.randomUUID();
    ExperimentProposal approved = proposal(ExperimentProposal.GovernanceState.APPROVED, id);
    when(repository.findById(id)).thenReturn(java.util.Optional.of(approved));

    assertThatThrownBy(() -> service.activate(id, "operator", "activate"))
        .isInstanceOf(MarTechActivationException.class)
        .hasMessageContaining("UNCONFIGURED");
  }

  private ActivationResult acceptedResult(String destination) {
    return new ActivationResult(
        destination, "key", ActivationResult.Status.ACCEPTED, 1, 0, null, java.util.Map.of());
  }

  private ExperimentProposalService providerService() {
    AudienceActivation audiences = mock(AudienceActivation.class);
    CampaignRegistration campaigns = mock(CampaignRegistration.class);
    when(audiences.activate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(acceptedResult("audience"));
    when(campaigns.register(org.mockito.ArgumentMatchers.any()))
        .thenReturn(acceptedResult("campaign"));
    return new ExperimentProposalService(repository, definitions, timings, audiences, campaigns);
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
