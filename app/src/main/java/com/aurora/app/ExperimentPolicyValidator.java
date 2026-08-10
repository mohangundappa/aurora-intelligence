package com.aurora.app;

import com.aurora.decision.DecisionPolicy;
import com.aurora.experiments.ExperimentRegistry;
import com.aurora.experiments.UnknownExperimentException;
import org.springframework.stereotype.Component;

@Component
public class ExperimentPolicyValidator {
  public ExperimentPolicyValidator(DecisionPolicy policy, ExperimentRegistry experiments) {
    policy
        .experimentIds()
        .forEach(
            id -> {
              try {
                experiments.definition(id);
              } catch (UnknownExperimentException exception) {
                if (experiments.isDatabaseViewIncomplete()) {
                  throw new IllegalStateException(
                      "Cannot validate decision policy experiment '"
                          + id
                          + "' because database experiment definitions are unavailable",
                      exception);
                }
                throw new IllegalStateException(
                    "Decision policy references experiment '"
                        + id
                        + "' but no definition is registered. "
                        + exception.getMessage(),
                    exception);
              }
            });
  }
}
