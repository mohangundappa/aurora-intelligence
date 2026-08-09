package com.aurora.signals;

import com.aurora.cdp.SimulatedCdpAdapter;
import com.aurora.common.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SignalConsumer {
  private final SignalEngine engine;
  private final SimulatedCdpAdapter cdp;
  private final ObjectMapper mapper;

  public SignalConsumer(SignalEngine engine, SimulatedCdpAdapter cdp, ObjectMapper mapper) {
    this.engine = engine;
    this.cdp = cdp;
    this.mapper = mapper;
  }

  @KafkaListener(topics = "aurora.events.raw.v1", groupId = "aurora-signal-consumer")
  public void consume(String payload) {
    try {
      EventEnvelope event = mapper.readValue(payload, EventEnvelope.class);
      cdp.accept(event);
      engine.calculateAll(event.sessionId());
    } catch (Exception exception) {
      throw new IllegalArgumentException("Unable to deserialize raw event", exception);
    }
  }
}
