package com.aurora.agents;

import com.aurora.experiments.ActivationAttempt;
import com.aurora.experiments.ActivationAttemptRepository;
import com.aurora.experiments.ExperimentAnalysis;
import com.aurora.experiments.ExperimentAnalysisService;
import com.aurora.experiments.ExperimentProposal;
import com.aurora.experiments.ExperimentProposalRepository;
import com.aurora.experiments.ExperimentProposalService;
import com.aurora.objectives.MarketingObjective;
import com.aurora.objectives.MarketingObjectiveService;
import com.aurora.objectives.WorkflowStageTiming;
import com.aurora.objectives.WorkflowStageTimingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console")
public class WorkforceConsoleController {
  private final MarketingObjectiveService objectives;
  private final MarketingInsightRepository insights;
  private final ExperimentProposalService proposals;
  private final ExperimentAnalysisService analyses;
  private final AgentExecutionRepository executions;
  private final WorkflowStageTimingService timings;
  private final ActivationAttemptRepository activationAttempts;

  public WorkforceConsoleController(
      MarketingObjectiveService objectives,
      MarketingInsightRepository insights,
      ExperimentProposalService proposals,
      ExperimentAnalysisService analyses,
      AgentExecutionRepository executions,
      WorkflowStageTimingService timings,
      ActivationAttemptRepository activationAttempts) {
    this.objectives = objectives;
    this.insights = insights;
    this.proposals = proposals;
    this.analyses = analyses;
    this.executions = executions;
    this.timings = timings;
    this.activationAttempts = activationAttempts;
  }

  @GetMapping("/workforce")
  public WorkforceView workforce() {
    List<MarketingInsight> allInsights = insights.findAll();
    List<ExperimentProposal> allProposals = proposals.list();
    List<AgentExecution> allExecutions = executions.findAll();
    List<ActivationAttempt> allAttempts = activationAttempts.findAll();
    return new WorkforceView(
        objectives.list().stream()
            .map(objective -> objectiveView(objective, allInsights, allProposals, allExecutions))
            .toList(),
        allExecutions,
        allAttempts);
  }

  private ObjectiveView objectiveView(
      MarketingObjective objective,
      List<MarketingInsight> allInsights,
      List<ExperimentProposal> allProposals,
      List<AgentExecution> allExecutions) {
    List<ExperimentProposal> objectiveProposals =
        allProposals.stream()
            .filter(proposal -> proposal.objectiveId().equals(objective.objectiveId()))
            .toList();
    return new ObjectiveView(
        objective,
        allInsights.stream()
            .filter(insight -> insight.objectiveId().equals(objective.objectiveId()))
            .toList(),
        objectiveProposals.stream().map(this::proposalView).toList(),
        allExecutions.stream()
            .filter(execution -> execution.objectiveId().equals(objective.objectiveId()))
            .toList(),
        timings.findByObjectiveId(objective.objectiveId()));
  }

  private ProposalView proposalView(ExperimentProposal proposal) {
    return new ProposalView(
        proposal,
        proposals.audit(proposal.proposalId()),
        proposals.activationAttempts(proposal.proposalId()),
        analyses.list(proposal.experimentId()));
  }

  public record WorkforceView(
      List<ObjectiveView> objectives,
      List<AgentExecution> executions,
      List<ActivationAttempt> activationAttempts) {}

  public record ObjectiveView(
      MarketingObjective objective,
      List<MarketingInsight> insights,
      List<ProposalView> proposals,
      List<AgentExecution> executions,
      List<WorkflowStageTiming> timings) {}

  public record ProposalView(
      ExperimentProposal proposal,
      List<ExperimentProposalRepository.GovernanceAudit> audit,
      List<ActivationAttempt> activationAttempts,
      List<ExperimentAnalysis> analyses) {}
}
