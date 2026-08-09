package com.aurora.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
public class IngestController {
  private final IngestService service;

  public IngestController(IngestService service) {
    this.service = service;
  }

  @PostMapping
  public IngestResult ingest(@RequestBody @Valid JsonNode body) {
    return service.ingest(body);
  }

  @PostMapping("/replay")
  public java.util.Map<String, Object> replay(@RequestParam String sessionId) {
    return java.util.Map.of("sessionId", sessionId, "replayed", service.replay(sessionId));
  }
}
