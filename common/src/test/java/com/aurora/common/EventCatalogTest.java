package com.aurora.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventCatalogTest {
  @Test
  void rejectsMissingPayloadProperty() {
    EventEnvelope event =
        new EventEnvelope(
            UUID.randomUUID(),
            "DESTINATION_SEARCHED",
            Instant.now(),
            Instant.now(),
            "1.0",
            "test",
            "session",
            "anonymous",
            null,
            "correlation",
            new EventEnvelope.Consent(true, true),
            Map.of());

    assertThat(EventCatalog.validate(event)).contains("destination");
  }

  @Test
  void acceptsCompletePayload() {
    EventEnvelope event =
        new EventEnvelope(
            UUID.randomUUID(),
            "DESTINATION_SEARCHED",
            Instant.now(),
            Instant.now(),
            "1.0",
            "test",
            "session",
            "anonymous",
            null,
            "correlation",
            new EventEnvelope.Consent(true, true),
            Map.of("destination", "Miami"));

    assertThat(EventCatalog.validate(event)).isNull();
  }
}
