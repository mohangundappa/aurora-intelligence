package com.aurora.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurora.context.ContextCache;
import com.aurora.context.ContextService;
import com.aurora.ingest.EventRepository;
import com.aurora.ingest.IngestService;
import com.aurora.signals.SignalConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PostgresIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("aurora")
          .withUsername("aurora")
          .withPassword("aurora");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired IngestService ingest;
  @Autowired EventRepository events;
  @Autowired SignalConsumer consumer;
  @Autowired ContextService context;
  @Autowired ContextCache cache;
  @Autowired ObjectMapper mapper;

  @Test
  void postgresContainerStartsForReplayablePersistence() {
    assertThat(POSTGRES.isRunning()).isTrue();
  }

  @Test
  void ingestConsumeContextDecisionReplayAndIdentityStitch() throws Exception {
    String session = UUID.randomUUID().toString();
    JsonNode search =
        event("DESTINATION_SEARCHED", session, "anon-it", "{\"destination\":\"Miami\"}");
    JsonNode invalid = event("DESTINATION_SEARCHED", session, "anon-it", "{}");
    JsonNode identify =
        event("CUSTOMER_IDENTIFIED", session, "anon-it", "{\"customerId\":\"customer-it\"}");

    assertThat(ingest.ingest(mapper.createArrayNode().add(search).add(invalid)))
        .satisfies(
            result -> {
              assertThat(result.accepted()).isEqualTo(1);
              assertThat(result.quarantined()).isEqualTo(1);
            });
    assertThat(ingest.ingest(search).duplicates()).isEqualTo(1);

    consumer.consume(mapper.writeValueAsString(events.findBySession(session).get(0)));
    assertThat(context.forSession(session).activeSignals()).isNotEmpty();

    ingest.ingest(identify);
    consumer.consume(
        mapper.writeValueAsString(
            events.findBySession(session).stream()
                .filter(event -> "CUSTOMER_IDENTIFIED".equals(event.eventName()))
                .findFirst()
                .orElseThrow()));
    cache.evict(session);
    var stitched = context.forSession(session);
    assertThat(stitched.profile().identity().identified()).isTrue();
    assertThat(stitched.profile().identityTimeline()).hasSize(1);
    assertThat(stitched.activeSignals()).anyMatch(signal -> signal.value() > 0);

    int replayed = ingest.replay(session);
    assertThat(replayed).isEqualTo(2);
  }

  private JsonNode event(String name, String session, String anonymous, String payload)
      throws Exception {
    Instant now = Instant.now();
    return mapper.readTree(
        """
        {
          "eventId": "%s",
          "eventName": "%s",
          "eventTime": "%s",
          "receivedTime": "%s",
          "schemaVersion": "1.0",
          "source": "integration-test",
          "sessionId": "%s",
          "anonymousId": "%s",
          "correlationId": "%s",
          "consent": {"analytics": true, "personalization": true},
          "payload": %s
        }
        """
            .formatted(
                UUID.randomUUID(), name, now, now, session, anonymous, UUID.randomUUID(), payload));
  }
}
