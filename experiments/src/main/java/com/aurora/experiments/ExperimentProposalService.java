package com.aurora.experiments;

import com.aurora.objectives.WorkflowStage;
import com.aurora.objectives.WorkflowStageTimingService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExperimentProposalService {
  private final ExperimentProposalRepository repository;
  private final ExperimentDefinitionService definitions;
  private final WorkflowStageTimingService timings;

  public ExperimentProposalService(
      ExperimentProposalRepository repository,
      ExperimentDefinitionService definitions,
      WorkflowStageTimingService timings) {
    this.repository = repository;
    this.definitions = definitions;
    this.timings = timings;
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
        java.time.Duration.between(started, completed).toMillis(),
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
    Instant started = Instant.now();
    definitions.save(proposal.toDraftDefinition());
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
        java.time.Duration.between(started, completed).toMillis(),
        actor,
        started,
        completed);
    return get(proposalId);
  }

  public List<ExperimentProposalRepository.GovernanceAudit> audit(UUID proposalId) {
    get(proposalId);
    return repository.audit(proposalId);
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
}
