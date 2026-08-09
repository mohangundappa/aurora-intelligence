package com.aurora.ingest;

import com.aurora.common.EventEnvelope;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DefaultEventPublisher implements EventPublisher {
  private final KafkaTemplate<String, EventEnvelope> kafka;

  public DefaultEventPublisher(KafkaTemplate<String, EventEnvelope> kafka) {
    this.kafka = kafka;
  }

  @Override
  public void publish(EventEnvelope event) {
    kafka.send("aurora.events.raw.v1", event.eventId().toString(), event);
  }
}
