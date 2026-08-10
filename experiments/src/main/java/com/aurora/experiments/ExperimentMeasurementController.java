package com.aurora.experiments;

import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import com.aurora.common.EventEnvelope;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider-neutral measurement ingress used by integrations and the deterministic local showcase.
 */
@RestController
@RequestMapping("/api/experiments")
public class ExperimentMeasurementController {
  private final ExperimentService experiments;

  public ExperimentMeasurementController(ExperimentService experiments) {
    this.experiments = experiments;
  }

  @PostMapping("/{experimentId}/exposures")
  public void recordExposure(
      @PathVariable String experimentId, @RequestBody ExposureRequest request) {
    experiments.recordExposure(
        new Decision(
            "MEASURED_EXPERIENCE",
            "MEASURED_EXPERIENCE",
            "web",
            List.of("MEASUREMENT_INGRESS"),
            "measurement",
            experimentId,
            "Exposure recorded through the measurement ingress.",
            request.sessionId(),
            request.correlationId()),
        new CdpProfile(
            request.anonymousId(),
            request.customerId(),
            new CdpProfile.Identity(request.anonymousId(), request.customerId(), false),
            new CdpProfile.Loyalty("STANDARD", 0, true),
            new CdpProfile.ConsentState(true, true),
            Map.of(),
            java.util.Set.of(),
            List.of()));
  }

  @GetMapping("/{experimentId}/exposures")
  public List<ExperimentService.Exposure> exposures(@PathVariable String experimentId) {
    return experiments.exposures(experimentId);
  }

  @PostMapping("/{experimentId}/outcomes")
  public void recordOutcome(@PathVariable String experimentId, @RequestBody EventEnvelope event) {
    experiments.recordOutcome(event);
  }

  public record ExposureRequest(
      String anonymousId, String customerId, String sessionId, String correlationId) {}
}
