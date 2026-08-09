package com.aurora.signals;

import com.aurora.cdp.SimulatedCdpAdapter;
import com.aurora.common.EventEnvelope;
import com.aurora.identity.IdentityStitcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SignalConsumer {
  private final SignalEngine engine;
  private final SimulatedCdpAdapter cdp;
  private final ObjectMapper mapper;
  private final IdentityStitcher identity;

  public SignalConsumer(
      SignalEngine engine,
      SimulatedCdpAdapter cdp,
      ObjectMapper mapper,
      IdentityStitcher identity) {
    this.engine = engine;
    this.cdp = cdp;
    this.mapper = mapper;
    this.identity = identity;
  }

  @KafkaListener(topics = "aurora.events.raw.v1", groupId = "aurora-signal-consumer")
  public void consume(String payload) {
    try {
      EventEnvelope event = mapper.readValue(payload, EventEnvelope.class);
      if ("CUSTOMER_IDENTIFIED".equals(event.eventName())) {
        identity.process(event);
      } else {
        cdp.accept(event);
      }
      engine.calculateAll(event.sessionId());
    } catch (Exception exception) {
      throw new IllegalArgumentException("Unable to deserialize raw event", exception);
    }
  }
}
