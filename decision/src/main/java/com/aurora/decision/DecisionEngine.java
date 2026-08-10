package com.aurora.decision;

import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import com.aurora.common.SignalSnapshot;
import com.aurora.common.martech.ActivationRequest;
import com.aurora.common.martech.ActivationResult;
import com.aurora.common.martech.OfferDelivery;
import com.aurora.experiments.ExperimentService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DecisionEngine {
  private final DecisionPolicy policy;
  private final DecisionRepository repository;
  private final ExperimentService experiments;
  private final OfferDelivery delivery;

  public DecisionEngine(DecisionPolicy policy) {
    this(policy, null, null, null);
  }

  public DecisionEngine(
      DecisionPolicy policy, DecisionRepository repository, ExperimentService experiments) {
    this(policy, repository, experiments, null);
  }

  @Autowired
  public DecisionEngine(
      DecisionPolicy policy,
      DecisionRepository repository,
      ExperimentService experiments,
      OfferDelivery delivery) {
    this.policy = policy;
    this.repository = repository;
    this.experiments = experiments;
    this.delivery = delivery;
  }

  public Decision decide(
      String sessionId,
      CdpProfile profile,
      List<SignalSnapshot> signals,
      boolean personalizationConsent,
      String correlationId) {
    Decision decision = preview(sessionId, profile, signals, personalizationConsent, correlationId);
    if (personalizationConsent && profile.consent().personalization() && delivery != null) {
      ActivationResult activation =
          delivery.deliver(
              new ActivationRequest(decision.channel(), decisionPayload(decision), correlationId));
      decision = withActivationResult(decision, activation);
    }
    persist(decision, profile, signals);
    if (personalizationConsent && profile.consent().personalization() && experiments != null) {
      experiments.recordExposure(decision, profile);
    }
    return decision;
  }

  public Decision preview(
      String sessionId,
      CdpProfile profile,
      List<SignalSnapshot> signals,
      boolean personalizationConsent,
      String correlationId) {
    if (!personalizationConsent || !profile.consent().personalization()) {
      return new Decision(
          "STANDARD_WELCOME",
          "STANDARD_WELCOME",
          "web",
          List.of("CONSENT_NOT_GRANTED", "SAFE_DEFAULT"),
          policy.version(),
          null,
          "Personalization is disabled, so the standard welcome experience is shown.",
          sessionId,
          correlationId);
    }
    DecisionPolicy.DecisionPolicyResult result = policy.evaluate(signals);
    return new Decision(
        result.action(),
        result.experience(),
        policy.channel(),
        result.reasonCodes(),
        policy.version(),
        result.experimentId(),
        result.explanation(),
        sessionId,
        correlationId);
  }

  private void persist(Decision decision, CdpProfile profile, List<SignalSnapshot> signals) {
    if (repository != null) repository.save(decision, profile, signals);
  }

  private Map<String, Object> decisionPayload(Decision decision) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("experience", decision.experience());
    payload.put("action", decision.action());
    payload.put("sessionId", decision.sessionId());
    if (decision.experimentId() != null) {
      payload.put("experimentId", decision.experimentId());
    }
    payload.put("consentEnforced", true);
    return payload;
  }

  private Decision withActivationResult(Decision decision, ActivationResult activation) {
    String statusCode = "MARTECH_ACTIVATION_" + activation.status();
    List<String> reasonCodes =
        java.util.stream.Stream.concat(
                decision.reasonCodes().stream(), java.util.stream.Stream.of(statusCode))
            .toList();
    String explanation =
        activation.reason() == null
            ? decision.explanation()
            : decision.explanation() + " Marketing platform response: " + activation.reason();
    return new Decision(
        decision.action(),
        decision.experience(),
        decision.channel(),
        reasonCodes,
        decision.decisionVersion(),
        decision.experimentId(),
        explanation,
        decision.sessionId(),
        decision.correlationId());
  }
}
