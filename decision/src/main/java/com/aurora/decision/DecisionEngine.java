package com.aurora.decision;

import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import com.aurora.common.SignalSnapshot;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DecisionEngine {
  private final DecisionPolicy policy;
  private final DecisionRepository repository;

  public DecisionEngine(DecisionPolicy policy) {
    this(policy, null);
  }

  @Autowired
  public DecisionEngine(DecisionPolicy policy, DecisionRepository repository) {
    this.policy = policy;
    this.repository = repository;
  }

  public Decision decide(
      String sessionId,
      CdpProfile profile,
      List<SignalSnapshot> signals,
      boolean personalizationConsent,
      String correlationId) {
    if (!personalizationConsent || !profile.consent().personalization()) {
      Decision decision =
          new Decision(
              "STANDARD_WELCOME",
              "STANDARD_WELCOME",
              "web",
              List.of("CONSENT_NOT_GRANTED", "SAFE_DEFAULT"),
              policy.version(),
              null,
              "Personalization is disabled, so the standard welcome experience is shown.",
              sessionId,
              correlationId);
      persist(decision, profile, signals);
      return decision;
    }
    DecisionPolicy.DecisionPolicyResult result = policy.evaluate(signals);
    Decision decision =
        new Decision(
            result.action(),
            result.experience(),
            policy.channel(),
            result.reasonCodes(),
            policy.version(),
            result.ruleId(),
            result.explanation(),
            sessionId,
            correlationId);
    persist(decision, profile, signals);
    return decision;
  }

  private void persist(Decision decision, CdpProfile profile, List<SignalSnapshot> signals) {
    if (repository != null) repository.save(decision, profile, signals);
  }
}
