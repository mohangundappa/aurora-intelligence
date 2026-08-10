package com.aurora.objectives;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MarketingObjectiveService {
  private static final Map<MarketingObjective.Status, Set<MarketingObjective.Status>>
      LEGAL_TRANSITIONS =
          Map.of(
              MarketingObjective.Status.DRAFT, Set.of(MarketingObjective.Status.ACTIVE),
              MarketingObjective.Status.ACTIVE, Set.of(MarketingObjective.Status.COMPLETED),
              MarketingObjective.Status.COMPLETED, Set.of(MarketingObjective.Status.ARCHIVED),
              MarketingObjective.Status.ARCHIVED, Set.of());
  private final MarketingObjectiveRepository repository;
  private final WorkflowStageTimingService timings;

  public MarketingObjectiveService(
      MarketingObjectiveRepository repository, WorkflowStageTimingService timings) {
    this.repository = repository;
    this.timings = timings;
  }

  @Transactional
  public MarketingObjective create(CreateObjective command) {
    Instant started = Instant.now();
    MarketingObjective objective =
        new MarketingObjective(
            command.objectiveId() == null || command.objectiveId().isBlank()
                ? UUID.randomUUID().toString()
                : command.objectiveId(),
            command.name(),
            command.description(),
            command.businessGoal(),
            command.targetKpi(),
            command.targetValue(),
            command.targetAudience(),
            command.constraints(),
            command.startDate(),
            command.endDate(),
            MarketingObjective.Status.DRAFT,
            command.createdBy(),
            Instant.now());
    repository.save(objective);
    Instant completed = Instant.now();
    timings.record(
        objective.objectiveId(),
        WorkflowStage.OBJECTIVE_DEFINITION,
        java.time.Duration.between(started, completed).toMillis(),
        command.createdBy(),
        started,
        completed);
    return objective;
  }

  public MarketingObjective get(String objectiveId) {
    return repository
        .findById(objectiveId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Unknown marketing objective " + objectiveId));
  }

  public List<MarketingObjective> list() {
    return repository.findAll();
  }

  @Transactional
  public MarketingObjective transition(
      String objectiveId, MarketingObjective.Status target, String actor) {
    MarketingObjective current = get(objectiveId);
    if (!LEGAL_TRANSITIONS.getOrDefault(current.status(), Set.of()).contains(target)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Illegal marketing objective lifecycle transition: "
              + current.status()
              + " -> "
              + target);
    }
    repository.transition(objectiveId, target);
    repository.audit(objectiveId, actor, current.status(), target);
    return get(objectiveId);
  }

  public record CreateObjective(
      String objectiveId,
      String name,
      String description,
      String businessGoal,
      String targetKpi,
      java.math.BigDecimal targetValue,
      String targetAudience,
      Map<String, Object> constraints,
      java.time.LocalDate startDate,
      java.time.LocalDate endDate,
      String createdBy) {}
}
