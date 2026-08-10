package com.aurora.agents;

import com.aurora.objectives.MarketingObjective;

public record ExperimentationInput(MarketingObjective objective, MarketingInsight insight) {
  public ExperimentationInput {
    if (objective == null) throw new IllegalArgumentException("objective is required");
    if (insight == null) throw new IllegalArgumentException("insight is required");
    if (!objective.objectiveId().equals(insight.objectiveId())) {
      throw new IllegalArgumentException("insight must belong to the objective");
    }
  }
}
