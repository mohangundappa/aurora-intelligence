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
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ApiController {
  private static final Logger log = LoggerFactory.getLogger(ApiController.class);
  private final EventPublisher publisher; private final SignalEngine signals; private final SimulatedCdpAdapter cdp; private final DecisionEngine decisions;
  private final JdbcTemplate jdbc;
  private final Map<UUID,EventEnvelope> raw = new ConcurrentHashMap<>(); private final Map<UUID,String> quarantine = new ConcurrentHashMap<>();
  public ApiController(EventPublisher p, SignalEngine s, SimulatedCdpAdapter c, DecisionEngine d, JdbcTemplate jdbc) { publisher=p;signals=s;cdp=c;decisions=d;this.jdbc=jdbc; }
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
    try {
      String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(event.payload());
      jdbc.update(connection -> {
        var ps = connection.prepareStatement("INSERT INTO raw_events(event_id,event_name,event_time,received_time,schema_version,source,session_id,anonymous_id,customer_id,correlation_id,payload) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb)");
        ps.setObject(1,event.eventId()); ps.setString(2,event.eventName()); ps.setTimestamp(3, java.sql.Timestamp.from(event.eventTime())); ps.setTimestamp(4, java.sql.Timestamp.from(event.receivedTime()));
        ps.setString(5,event.schemaVersion()); ps.setString(6,event.source()); ps.setString(7,event.sessionId()); ps.setString(8,event.anonymousId());
        if (event.customerId() == null) ps.setNull(9, Types.VARCHAR); else ps.setString(9,event.customerId());
        ps.setString(10,event.correlationId()); ps.setString(11,payload); return ps;
      });
    } catch (Exception ex) { log.warn("Unable to persist raw event {}", event.eventId(), ex); }
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
