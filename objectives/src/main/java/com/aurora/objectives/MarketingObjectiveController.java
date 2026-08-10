package com.aurora.objectives;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objectives")
public class MarketingObjectiveController {
  private final MarketingObjectiveService objectives;

  public MarketingObjectiveController(MarketingObjectiveService objectives) {
    this.objectives = objectives;
  }

  @PostMapping
  public MarketingObjective create(@RequestBody MarketingObjectiveService.CreateObjective command) {
    return objectives.create(command);
  }

  @GetMapping
  public List<MarketingObjective> list() {
    return objectives.list();
  }

  @GetMapping("/{objectiveId}")
  public MarketingObjective get(@PathVariable String objectiveId) {
    return objectives.get(objectiveId);
  }

  @PostMapping("/{objectiveId}/status/{status}")
  public MarketingObjective transition(
      @PathVariable String objectiveId,
      @PathVariable MarketingObjective.Status status,
      @RequestParam(defaultValue = "console-presenter") String actor) {
    return objectives.transition(objectiveId, status, actor);
  }
}
