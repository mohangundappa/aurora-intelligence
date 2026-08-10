package com.aurora.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurora.common.SignalDefinition;
import com.aurora.context.ContextCache;
import com.aurora.context.ContextService;
import com.aurora.experiments.ExperimentDefinition;
import com.aurora.experiments.ExperimentDefinitionService;
import com.aurora.experiments.ExperimentRegistry;
import com.aurora.ingest.EventRepository;
import com.aurora.ingest.IngestService;
import com.aurora.signals.SignalConsumer;
import com.aurora.signals.SignalEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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
  @Autowired SignalEngine signalEngine;
  @Autowired ContextService context;
  @Autowired ContextCache cache;
  @Autowired ObjectMapper mapper;
  @Autowired ExperimentDefinitionService experimentDefinitions;
  @Autowired ExperimentRegistry experimentRegistry;

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

  @Test
  void personalizationEvidenceIsExcludedPerEventAndSafeDefaultIsShown() throws Exception {
    String session = UUID.randomUUID().toString();
    JsonNode denied =
        event(
            "DESTINATION_SEARCHED",
            session,
            "anon-consent-it",
            "{\"destination\":\"Miami\"}",
            false);
    assertThat(ingest.ingest(denied).accepted()).isEqualTo(1);
    consumer.consume(mapper.writeValueAsString(events.findBySession(session).get(0)));
    cache.evict(session);

    var context = this.context.forSession(session);
    assertThat(context.activeSignals()).isEmpty();
    assertThat(context.recommendedAction().experience()).isEqualTo("STANDARD_WELCOME");
    assertThat(context.recommendedAction().reasonCodes()).contains("CONSENT_NOT_GRANTED");
  }

  @Test
  void nonConsentRequiredDefinitionStillReceivesDeniedEvidence() throws Exception {
    String session = UUID.randomUUID().toString();
    ingest.ingest(
        event(
            "DESTINATION_SEARCHED",
            session,
            "anon-unrestricted",
            "{\"destination\":\"Miami\"}",
            false));
    SignalDefinition definition =
        new SignalDefinition(
            "test",
            "1.0",
            java.util.List.of("DESTINATION_SEARCHED"),
            SignalDefinition.CalculationType.AGGREGATION,
            "real-time",
            "30d",
            "0-100",
            "standard",
            "15m",
            "24h",
            false,
            "test",
            SignalDefinition.LifecycleStatus.DEPLOYED,
            "test");

    assertThat(signalEngine.evidenceForDefinition(definition, events.findBySession(session)))
        .hasSize(1);
  }

  @Test
  void databaseExperimentDefinitionIsResolvableWithoutRedeploy() {
    ExperimentDefinition definition =
        new ExperimentDefinition(
            "database-integration-experiment",
            "Database integration experiment",
            "Loaded from the experiment definitions database tables.",
            List.of(
                new ExperimentDefinition.Variant("control", 50),
                new ExperimentDefinition.Variant("treatment", 50)),
            "BOOKING_COMPLETED",
            30,
            ExperimentDefinition.LifecycleStatus.DEPLOYED);

    experimentDefinitions.save(definition);

    assertThat(experimentRegistry.definition(definition.id())).isEqualTo(definition);
  }

  @Test
  void aggregateFunnelOnlyCountsSessionsThatPassedEarlierStages() throws Exception {
    String complete = UUID.randomUUID().toString();
    String started = UUID.randomUUID().toString();
    String rateOnly = UUID.randomUUID().toString();
    var batch = mapper.createArrayNode();
    for (String stage :
        new String[] {
          "DESTINATION_SEARCHED",
          "PROPERTY_VIEWED",
          "ROOM_VIEWED",
          "RATE_VIEWED",
          "BOOKING_STARTED",
          "BOOKING_COMPLETED"
        }) {
      batch.add(funnelEvent(stage, complete, "complete"));
    }
    for (String stage :
        new String[] {"DESTINATION_SEARCHED", "PROPERTY_VIEWED", "BOOKING_STARTED"}) {
      batch.add(funnelEvent(stage, started, "started"));
    }
    for (String stage :
        new String[] {"DESTINATION_SEARCHED", "PROPERTY_VIEWED", "ROOM_VIEWED", "RATE_VIEWED"}) {
      batch.add(funnelEvent(stage, rateOnly, "rate"));
    }
    ingest.ingest(batch);

    assertThat(events.funnel(null).stages())
        .extracting(EventRepository.FunnelStage::sessions)
        .containsExactly(3L, 3L, 2L, 2L, 1L, 1L);
  }

  private JsonNode event(String name, String session, String anonymous, String payload)
      throws Exception {
    return event(name, session, anonymous, payload, true);
  }

  private JsonNode event(
      String name, String session, String anonymous, String payload, boolean personalization)
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
          "consent": {"analytics": true, "personalization": %s},
          "payload": %s
        }
        """
            .formatted(
                UUID.randomUUID(),
                name,
                now,
                now,
                session,
                anonymous,
                UUID.randomUUID(),
                personalization,
                payload));
  }

  private JsonNode funnelEvent(String name, String session, String anonymous) throws Exception {
    String payload =
        switch (name) {
          case "DESTINATION_SEARCHED" -> "{\"destination\":\"Miami\"}";
          case "PROPERTY_VIEWED" -> "{\"propertyId\":\"aurora-miami\"}";
          case "ROOM_VIEWED" -> "{\"propertyId\":\"aurora-miami\",\"roomId\":\"family-suite\"}";
          case "RATE_VIEWED" ->
              "{\"propertyId\":\"aurora-miami\",\"roomId\":\"family-suite\",\"rate\":389}";
          case "BOOKING_STARTED" -> "{\"propertyId\":\"aurora-miami\"}";
          case "BOOKING_COMPLETED" ->
              "{\"propertyId\":\"aurora-miami\",\"bookingId\":\"booking-"
                  + session.substring(0, 8)
                  + "\"}";
          default -> "{}";
        };
    return event(name, session, anonymous, payload);
  }
}
