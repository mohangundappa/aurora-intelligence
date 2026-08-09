package com.aurora.signals;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/signals/lifecycle")
public class SignalLifecycleController {
  private final SignalLifecycleService lifecycle;

  public SignalLifecycleController(SignalLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @GetMapping
  public List<SignalLifecycleService.SignalLifecycle> list() {
    return lifecycle.list();
  }

  @GetMapping("/{name}/audit")
  public List<SignalLifecycleService.SignalAudit> audit(@PathVariable String name) {
    return lifecycle.audit(name);
  }

  @PostMapping("/{name}/{status}")
  public SignalLifecycleService.SignalLifecycle transition(
      @PathVariable String name,
      @PathVariable String status,
      @RequestParam(defaultValue = "console-presenter") String actor) {
    return lifecycle.transition(name, status.toUpperCase(), actor);
  }
}
