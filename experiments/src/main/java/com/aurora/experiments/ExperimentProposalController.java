package com.aurora.experiments;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiment-proposals")
public class ExperimentProposalController {
  private final ExperimentProposalService proposals;

  public ExperimentProposalController(ExperimentProposalService proposals) {
    this.proposals = proposals;
  }

  @GetMapping
  public List<ExperimentProposal> list() {
    return proposals.list();
  }

  @GetMapping("/{proposalId}")
  public ExperimentProposal get(@PathVariable UUID proposalId) {
    return proposals.get(proposalId);
  }

  @GetMapping("/{proposalId}/audit")
  public List<ExperimentProposalRepository.GovernanceAudit> audit(@PathVariable UUID proposalId) {
    return proposals.audit(proposalId);
  }

  @PostMapping("/{proposalId}/approve")
  public ExperimentProposal approve(
      @PathVariable UUID proposalId, @RequestBody TransitionRequest request) {
    return proposals.approve(proposalId, request.actor(), request.reason());
  }

  @PostMapping("/{proposalId}/reject")
  public ExperimentProposal reject(
      @PathVariable UUID proposalId, @RequestBody TransitionRequest request) {
    return proposals.reject(proposalId, request.actor(), request.reason());
  }

  @PostMapping("/{proposalId}/activate")
  public ExperimentProposal activate(
      @PathVariable UUID proposalId, @RequestBody TransitionRequest request) {
    return proposals.activate(proposalId, request.actor(), request.reason());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> invalid(IllegalArgumentException exception) {
    return Map.of("error", exception.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> illegalTransition(IllegalStateException exception) {
    return Map.of("error", exception.getMessage());
  }

  public record TransitionRequest(String actor, String reason) {}
}
