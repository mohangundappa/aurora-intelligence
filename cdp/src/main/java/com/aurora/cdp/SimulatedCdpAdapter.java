package com.aurora.cdp;
import com.aurora.common.EventEnvelope;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class SimulatedCdpAdapter implements CdpAdapter {
  private final Map<String, Map<String,Object>> profiles = new ConcurrentHashMap<>();
  public void accept(EventEnvelope event) {
    profiles.computeIfAbsent(event.anonymousId(), id -> new ConcurrentHashMap<>())
      .put("lastEvent", event.eventName());
  }
  public Map<String,Object> profile(String anonymousId) { return profiles.getOrDefault(anonymousId, Map.of()); }
}
