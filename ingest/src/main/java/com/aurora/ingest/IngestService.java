package com.aurora.ingest;

import com.aurora.common.EventCatalog;
import com.aurora.common.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestService {
  private final EventRepository repository;
  private final EventPublisher publisher;
  private final ObjectMapper mapper;

  public IngestService(EventRepository repository, EventPublisher publisher, ObjectMapper mapper) {
    this.repository = repository;
    this.publisher = publisher;
    this.mapper = mapper;
  }

  @Transactional
  public IngestResult ingest(JsonNode body) {
    List<JsonNode> nodes = new ArrayList<>();
    if (body.isArray()) body.forEach(nodes::add);
    else nodes.add(body);

    int accepted = 0;
    int duplicates = 0;
    int quarantined = 0;
    List<UUID> acceptedIds = new ArrayList<>();
    List<UUID> quarantinedIds = new ArrayList<>();

    for (JsonNode node : nodes) {
      UUID eventId = node.has("eventId") ? parseId(node.get("eventId").asText()) : null;
      try {
        EventEnvelope event = mapper.treeToValue(node, EventEnvelope.class);
        String validationError = EventCatalog.validate(event);
        if (validationError != null) {
          repository.quarantine(eventId, validationError, node.toString());
          quarantined++;
          if (eventId != null) quarantinedIds.add(eventId);
          continue;
        }
        if (repository.exists(event.eventId())) {
          duplicates++;
          continue;
        }
        if (!repository.save(event)) {
          duplicates++;
          continue;
        }
        publisher.publish(event);
        accepted++;
        acceptedIds.add(event.eventId());
      } catch (JsonProcessingException | IllegalArgumentException exception) {
        repository.quarantine(eventId, exception.getMessage(), node.toString());
        quarantined++;
        if (eventId != null) quarantinedIds.add(eventId);
      }
    }
    return new IngestResult(accepted, duplicates, quarantined, acceptedIds, quarantinedIds);
  }

  public int replay(String sessionId) {
    List<EventEnvelope> events = repository.findBySession(sessionId);
    events.forEach(publisher::publish);
    return events.size();
  }

  private UUID parseId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
