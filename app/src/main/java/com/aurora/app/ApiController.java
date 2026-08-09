package com.aurora.app;
import com.aurora.common.*;
import com.aurora.cdp.SimulatedCdpAdapter;
import com.aurora.context.CustomerContext;
import com.aurora.decision.DecisionEngine;
import com.aurora.ingest.EventPublisher;
import com.aurora.signals.SignalEngine;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final EventPublisher publisher; private final SignalEngine signals; private final SimulatedCdpAdapter cdp; private final DecisionEngine decisions;
  private final Map<UUID,EventEnvelope> raw = new ConcurrentHashMap<>(); private final Map<UUID,String> quarantine = new ConcurrentHashMap<>();
  public ApiController(EventPublisher p, SignalEngine s, SimulatedCdpAdapter c, DecisionEngine d) { publisher=p;signals=s;cdp=c;decisions=d; }
  @PostMapping("/v1/events") public Map<String,Object> ingest(@RequestBody JsonNode body) throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    List<EventEnvelope> items = new ArrayList<>();
    if (body.isArray()) body.forEach(node -> items.add(mapper.convertValue(node, EventEnvelope.class)));
    else items.add(mapper.treeToValue(body, EventEnvelope.class));
    for (EventEnvelope event : items) envelope(event);
    return Map.of("accepted", items.size(), "eventIds", items.stream().map(e -> e.eventId().toString()).toList());
  }
  private Map<String,Object> envelope(EventEnvelope event) {
    if (raw.putIfAbsent(event.eventId(), event) != null) return Map.of("status","duplicate","eventId",event.eventId());
    publisher.publish(event); return Map.of("status","accepted","eventId",event.eventId(),"correlationId",event.correlationId());
  }
  @GetMapping("/sessions/{sessionId}/context") public CustomerContext context(@PathVariable String sessionId) {
    SignalResult signal=signals.calculate(sessionId); String anon=signals.events(sessionId).stream().findFirst().map(EventEnvelope::anonymousId).orElse("");
    return new CustomerContext(sessionId, signal, cdp.profile(anon));
  }
  @GetMapping("/sessions/{sessionId}/decision") public Decision decision(@PathVariable String sessionId,
      @RequestParam(defaultValue="true") boolean consent) {
    SignalResult s=signals.calculate(sessionId); String corr=s != null?s.correlationId():UUID.randomUUID().toString();
    return decisions.decide(sessionId,s,consent,corr);
  }
  @GetMapping("/sessions/{sessionId}/events") public List<EventEnvelope> events(@PathVariable String sessionId) { return signals.events(sessionId); }
  @GetMapping("/signals/definitions") public SignalDefinition definition() { return signals.definition(); }
  @GetMapping("/console/sessions/{sessionId}") public Map<String,Object> console(@PathVariable String sessionId) {
    return Map.of("events",events(sessionId),"context",context(sessionId),"decision",decision(sessionId,true));
  }
}
