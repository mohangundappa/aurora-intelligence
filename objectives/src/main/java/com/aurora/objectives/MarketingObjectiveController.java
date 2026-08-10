package com.aurora.objectives;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> invalidObjective(IllegalArgumentException exception) {
    return Map.of("error", exception.getMessage());
  }
}
