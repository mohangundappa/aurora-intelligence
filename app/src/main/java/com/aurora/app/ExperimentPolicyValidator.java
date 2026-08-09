package com.aurora.app;

import com.aurora.decision.DecisionPolicy;
import com.aurora.experiments.ExperimentRegistry;
import org.springframework.stereotype.Component;

@Component
public class ExperimentPolicyValidator {
  public ExperimentPolicyValidator(DecisionPolicy policy, ExperimentRegistry experiments) {
    policy.experimentIds().forEach(experiments::definition);
  }
}
