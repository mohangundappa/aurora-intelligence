package com.aurora.signals;

import com.aurora.common.*;
import org.yaml.snakeyaml.Yaml;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SignalEngine {
  private final Map<String, List<EventEnvelope>> events = new ConcurrentHashMap<>();
  private final SignalDefinition definition;
  public SignalEngine() {
    try (InputStream in = getClass().getResourceAsStream("/signals/destination-intent.yaml")) {
      Map<String,Object> m = new Yaml().load(in);
      definition = new SignalDefinition((String)m.get("name"), (String)m.get("version"),
        (List<String>)m.get("inputs"), SignalDefinition.CalculationType.valueOf(((String)m.get("calculationType")).toUpperCase()),
        (String)m.get("tier"), (String)m.get("lookback"), (String)m.get("outputRange"), (String)m.get("confidence"),
        (String)m.get("freshness"), (String)m.get("expiry"), (Boolean)m.get("consentRequired"),
        (String)m.get("explanationTemplate"), SignalDefinition.LifecycleStatus.valueOf(((String)m.get("lifecycleStatus")).toUpperCase()),
        (String)m.get("owner"));
    } catch (Exception e) { throw new IllegalStateException("Unable to load signal definition", e); }
  }
  public void accept(EventEnvelope event) { events.computeIfAbsent(event.sessionId(), k -> new ArrayList<>()).add(event); }
  public SignalResult calculate(String sessionId) {
    List<EventEnvelope> sessionEvents = events.getOrDefault(sessionId, List.of());
    EventEnvelope match = sessionEvents.stream().filter(e -> "DESTINATION_SEARCHED".equals(e.eventName())).reduce((a,b)->b).orElse(null);
    if (match == null || !match.consent().personalization()) return null;
    String destination = String.valueOf(match.payload().getOrDefault("destination", "your next destination"));
    return new SignalResult(definition.name(), 85, .92, "fresh", definition.explanationTemplate().replace("{destination}", destination),
      sessionId, match.correlationId());
  }
  public SignalDefinition definition() { return definition; }
  public List<EventEnvelope> events(String sessionId) { return events.getOrDefault(sessionId, List.of()); }
}
