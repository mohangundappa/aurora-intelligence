package com.aurora.experiments;

import com.aurora.common.martech.ActivationRequest;
import com.aurora.common.martech.ActivationResult;
import com.aurora.common.martech.AudienceActivation;
import com.aurora.common.martech.CampaignRegistration;
import com.aurora.objectives.WorkflowStage;
import com.aurora.objectives.WorkflowStageTimingService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExperimentProposalService {
  private final ExperimentProposalRepository repository;
  private final ExperimentDefinitionService definitions;
  private final WorkflowStageTimingService timings;
  private final AudienceActivation audiences;
  private final CampaignRegistration campaigns;
  private final ActivationAttemptRepository activationAttempts;

  @Autowired
  public ExperimentProposalService(
      ExperimentProposalRepository repository,
      ExperimentDefinitionService definitions,
      WorkflowStageTimingService timings,
      AudienceActivation audiences,
      CampaignRegistration campaigns,
      ActivationAttemptRepository activationAttempts) {
    this.repository = repository;
    this.definitions = definitions;
    this.timings = timings;
    this.audiences = audiences;
    this.campaigns = campaigns;
    this.activationAttempts = activationAttempts;
  }

  public ExperimentProposalService(
      ExperimentProposalRepository repository,
      ExperimentDefinitionService definitions,
      WorkflowStageTimingService timings) {
    this(repository, definitions, timings, null, null, null);
  }

  public ExperimentProposalService(
      ExperimentProposalRepository repository,
      ExperimentDefinitionService definitions,
      WorkflowStageTimingService timings,
      AudienceActivation audiences,
      CampaignRegistration campaigns) {
    this(repository, definitions, timings, audiences, campaigns, null);
  }

  public void save(ExperimentProposal proposal) {
    repository.save(proposal);
  }

  public ExperimentProposal get(UUID proposalId) {
    return repository
        .findById(proposalId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Unknown experiment proposal " + proposalId));
  }

  public List<ExperimentProposal> list() {
    return repository.findAll();
  }

  @Transactional
  public ExperimentProposal approve(UUID proposalId, String approver, String reason) {
    requireIdentity(approver, "approver identity");
    requireReason(reason);
    ExperimentProposal proposal = get(proposalId);
    requireState(proposal, ExperimentProposal.GovernanceState.PROPOSED);
    Instant started = Instant.now();
    repository.transition(
        proposalId,
        ExperimentProposal.GovernanceState.PROPOSED,
        ExperimentProposal.GovernanceState.APPROVED,
        approver,
        reason);
    Instant completed = Instant.now();
    timings.record(
        proposal.objectiveId(),
        WorkflowStage.APPROVAL,
        Duration.between(started, completed).toMillis(),
        approver,
        started,
        completed);
    return get(proposalId);
  }

  @Transactional
  public ExperimentProposal reject(UUID proposalId, String actor, String reason) {
    requireIdentity(actor, "actor identity");
    requireReason(reason);
    ExperimentProposal proposal = get(proposalId);
    requireState(proposal, ExperimentProposal.GovernanceState.PROPOSED);
    repository.transition(
        proposalId,
        ExperimentProposal.GovernanceState.PROPOSED,
        ExperimentProposal.GovernanceState.REJECTED,
        actor,
        reason);
    return get(proposalId);
  }

  @Transactional
  public ExperimentProposal activate(UUID proposalId, String actor, String reason) {
    requireIdentity(actor, "actor identity");
    requireReason(reason);
    ExperimentProposal proposal = get(proposalId);
    requireState(proposal, ExperimentProposal.GovernanceState.APPROVED);
    definitions.assertCanRegister(proposal.toDraftDefinition());
    Instant started = Instant.now();
    ActivationResult audienceResult = registerAudience(proposal);
    ActivationAttempt audienceAttempt =
        recordAttempt(proposal, "AUDIENCE", audienceRequest(proposal), audienceResult);
    requireAccepted("audience", audienceResult, audienceAttempt);
    ActivationResult campaignResult = registerCampaign(proposal);
    ActivationAttempt campaignAttempt =
        recordAttempt(proposal, "CAMPAIGN", campaignRequest(proposal), campaignResult);
    requireAccepted("campaign", campaignResult, campaignAttempt);
    definitions.saveAfterCommit(proposal.toDraftDefinition());
    repository.transition(
        proposalId,
        ExperimentProposal.GovernanceState.APPROVED,
        ExperimentProposal.GovernanceState.ACTIVATED,
        actor,
        reason);
    Instant completed = Instant.now();
    timings.record(
        proposal.objectiveId(),
        WorkflowStage.EXPERIMENT_CONFIGURATION,
        Duration.between(started, completed).toMillis(),
        actor,
        started,
        completed);
    return get(proposalId);
  }

  public List<ExperimentProposalRepository.GovernanceAudit> audit(UUID proposalId) {
    get(proposalId);
    return repository.audit(proposalId);
  }

  public List<ActivationAttempt> activationAttempts(UUID proposalId) {
    get(proposalId);
    return activationAttempts == null ? List.of() : activationAttempts.findByProposalId(proposalId);
  }

  private void requireState(
      ExperimentProposal proposal, ExperimentProposal.GovernanceState expected) {
    if (proposal.governanceState() != expected) {
      throw new IllegalStateException(
          "Illegal experiment proposal transition from "
              + proposal.governanceState()
              + "; expected "
              + expected);
    }
  }

  private void requireIdentity(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }

  private void requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("transition reason is required");
    }
  }

  private ActivationResult registerAudience(ExperimentProposal proposal) {
    if (audiences == null) {
      return new ActivationResult(
          proposal.targetAudience(),
          "disabled-" + proposal.proposalId(),
          ActivationResult.Status.UNCONFIGURED,
          0,
          0,
          "no audience activation provider is configured",
          Map.of());
    }
    ActivationRequest request = audienceRequest(proposal);
    try {
      return audiences.activate(request);
    } catch (RuntimeException exception) {
      return failedResult(request, "audience activation failed: " + message(exception));
    }
  }

  private ActivationResult registerCampaign(ExperimentProposal proposal) {
    if (campaigns == null) {
      return new ActivationResult(
          proposal.experimentId(),
          "disabled-" + proposal.proposalId(),
          ActivationResult.Status.UNCONFIGURED,
          0,
          0,
          "no campaign registration provider is configured",
          Map.of());
    }
    ActivationRequest request = campaignRequest(proposal);
    try {
      return campaigns.register(request);
    } catch (RuntimeException exception) {
      return failedResult(request, "campaign registration failed: " + message(exception));
    }
  }

  private void requireAccepted(
      String operation, ActivationResult result, ActivationAttempt activationAttempt) {
    if (result.status() != ActivationResult.Status.ACCEPTED) {
      throw new MarTechActivationException(
          operation, result, activationAttempt == null ? null : activationAttempt.attemptId());
    }
  }

  private ActivationRequest audienceRequest(ExperimentProposal proposal) {
    return new ActivationRequest(
        proposal.targetAudience(),
        Map.of(
            "audience",
            proposal.targetAudience(),
            "targetingSignal",
            proposal.targetingSignal(),
            "consentEnforced",
            true,
            "eligibilityEnforced",
            true),
        proposal.proposalId() + ":audience");
  }

  private ActivationRequest campaignRequest(ExperimentProposal proposal) {
    return new ActivationRequest(
        proposal.experimentId(),
        Map.of(
            "experimentId", proposal.experimentId(),
            "objectiveId", proposal.objectiveId(),
            "approvedArtifact", true),
        proposal.proposalId() + ":campaign");
  }

  private ActivationAttempt recordAttempt(
      ExperimentProposal proposal,
      String operation,
      ActivationRequest request,
      ActivationResult result) {
    if (activationAttempts == null) return null;
    ActivationAttempt attempt =
        ActivationAttempt.from(proposal.proposalId(), operation, request, result);
    activationAttempts.save(attempt);
    return attempt;
  }

  private ActivationResult failedResult(ActivationRequest request, String reason) {
    return new ActivationResult(
        request.destinationId(),
        request.idempotencyKey(),
        ActivationResult.Status.FAILED,
        0,
        0,
        reason,
        Map.of());
  }

  private String message(RuntimeException exception) {
    return exception.getMessage() == null
        ? exception.getClass().getSimpleName()
        : exception.getMessage();
  }
}
