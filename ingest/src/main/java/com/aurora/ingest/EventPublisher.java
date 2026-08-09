package com.aurora.ingest;

import com.aurora.common.EventEnvelope;

public interface EventPublisher {
  void publish(EventEnvelope event);
}
