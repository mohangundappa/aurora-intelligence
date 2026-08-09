package com.aurora.ingest;

import com.aurora.common.EventEnvelope;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InProcessEventPublisher implements EventPublisher {
  @Override
  public void publish(EventEnvelope event) {}
}
