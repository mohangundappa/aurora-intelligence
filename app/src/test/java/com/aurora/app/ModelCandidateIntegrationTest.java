package com.aurora.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aurora.models.CandidatePackageHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class ModelCandidateIntegrationTest {
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

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;

  @Test
  void registrationIsIdempotentAuditedAndDoesNotEnterModelLifecycle() throws Exception {
    String initiativeId = UUID.randomUUID().toString();
    String body = candidateBody(initiativeId);
    String packageHash = packageHash(body);

    String firstResponse =
        mvc.perform(
                post("/api/models/booking-intent/candidates")
                    .header("Idempotency-Key", packageHash)
                    .header("X-Aurora-Studio-Token", "studio-demo-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_WEIGHTS"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String candidateId = firstResponse.replaceAll(".*\"candidateId\":\"([^\"]+)\".*", "$1");

    mvc.perform(
            post("/api/models/booking-intent/candidates")
                .header("Idempotency-Key", packageHash)
                .header("X-Aurora-Studio-Token", "studio-demo-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.candidateId").value(candidateId))
        .andExpect(jsonPath("$.status").value("AWAITING_WEIGHTS"));

    mvc.perform(get("/api/models/booking-intent/candidates"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.candidateId == '" + candidateId + "')].status")
                .value("AWAITING_WEIGHTS"))
        .andExpect(
            jsonPath("$[?(@.candidateId == '" + candidateId + "')].packageContent.packageHash")
                .value(packageHash))
        .andExpect(
            jsonPath("$[?(@.candidateId == '" + candidateId + "')].packageContent.clientId")
                .value("studio-client"))
        .andExpect(
            jsonPath("$[?(@.candidateId == '" + candidateId + "')].packageContent.requirementId")
                .value("studio-requirement"));

    assertThat(
            jdbc.queryForObject(
                "select count(*) from model_candidates where model_name=? and package_hash=?",
                Integer.class,
                "booking-intent",
                packageHash))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from model_candidate_audit where package_hash=?",
                Integer.class,
                packageHash))
        .isEqualTo(2);

    mvc.perform(get("/api/models/booking-intent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    mvc.perform(post("/api/models/booking-intent/" + candidateId + "/approve"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/models/booking-intent/" + candidateId + "/deploy"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/models/booking-intent/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modelVersion").value("1.0"));
  }

  @Test
  void malformedPackageIsRejectedWithNamedProblem() throws Exception {
    String body =
        candidateBody(UUID.randomUUID().toString())
            .replace("\"clientId\": \"studio-client\"", "\"unexpected\": true");
    String packageHash = packageHash(body);

    mvc.perform(
            post("/api/models/booking-intent/candidates")
                .header("Idempotency-Key", packageHash)
                .header("X-Aurora-Studio-Token", "studio-demo-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Unknown candidate field: unexpected"));
  }

  @Test
  void multipleUnknownFieldsAreReportedInSortedOrder() throws Exception {
    String body =
        candidateBody(UUID.randomUUID().toString())
            .replace("\"clientId\": \"studio-client\"", "\"zeta\": true, \"alpha\": true");
    String packageHash = packageHash(body);

    mvc.perform(
            post("/api/models/booking-intent/candidates")
                .header("Idempotency-Key", packageHash)
                .header("X-Aurora-Studio-Token", "studio-demo-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Unknown candidate fields: alpha, zeta"));
  }

  @Test
  void missingRequiredPackageFieldIsRejected() throws Exception {
    String body =
        candidateBody(UUID.randomUUID().toString())
            .replaceFirst("\"packageHash\": \"[^\"]+\"", "\"packageHash\": \"\"");
    String packageHash = "";

    mvc.perform(
            post("/api/models/booking-intent/candidates")
                .header("Idempotency-Key", packageHash)
                .header("X-Aurora-Studio-Token", "studio-demo-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("packageHash is required"));
  }

  @Test
  void unknownModelVersionIsNotFound() throws Exception {
    mvc.perform(get("/api/models/booking-intent/does-not-exist/evaluation"))
        .andExpect(status().isNotFound());
  }

  @Test
  void suppliedHashMismatchIsRejectedBeforeIdempotentReplay() throws Exception {
    String body = candidateBody(UUID.randomUUID().toString());
    String realHash = packageHash(body);
    String forgedBody =
        body.replace("\"cohortSql\": \"select 1\"", "\"cohortSql\": \"select 999\"");

    mvc.perform(
            post("/api/models/booking-intent/candidates")
                .header("Idempotency-Key", realHash)
                .header("X-Aurora-Studio-Token", "studio-demo-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgedBody))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error").value("packageHash does not match candidate package content"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from model_candidates where package_hash=?",
                Integer.class,
                realHash))
        .isZero();
  }

  @Test
  void missingOrWrongStudioTokenIsRejected() throws Exception {
    String body = candidateBody(UUID.randomUUID().toString());
    String packageHash = packageHash(body);

    mvc.perform(
            post("/api/models/booking-intent/candidates")
                .header("Idempotency-Key", packageHash)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Invalid candidate registration token"));
    mvc.perform(
            post("/api/models/booking-intent/candidates")
                .header("Idempotency-Key", packageHash)
                .header("X-Aurora-Studio-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Invalid candidate registration token"));
  }

  @Test
  void malformedJsonUsesCandidateErrorShape() throws Exception {
    mvc.perform(
            post("/api/models/booking-intent/candidates")
                .header("X-Aurora-Studio-Token", "studio-demo-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("request body must be valid JSON"));
  }

  @Test
  void malformedJsonOnPredictionUsesGenericErrorMessage() throws Exception {
    mvc.perform(
            post("/api/models/booking-intent/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("request body must be valid JSON"));
  }

  @Test
  void candidateAuditIsAppendOnly() throws Exception {
    String body = candidateBody(UUID.randomUUID().toString());
    String packageHash = packageHash(body);
    mvc.perform(
            post("/api/models/booking-intent/candidates")
                .header("Idempotency-Key", packageHash)
                .header("X-Aurora-Studio-Token", "studio-demo-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    Long auditId =
        jdbc.queryForObject(
            "select audit_id from model_candidate_audit where package_hash=?",
            Long.class,
            packageHash);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update model_candidate_audit set action='TAMPERED' where audit_id=?", auditId))
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () -> jdbc.update("delete from model_candidate_audit where audit_id=?", auditId))
        .hasMessageContaining("append-only");
  }

  private String candidateBody(String initiativeId) throws Exception {
    String body =
        """
        {
          "studioInitiativeId": "%s",
          "requirementId": "studio-requirement",
          "packageHash": "",
          "modelName": "booking-intent",
          "targeting": {"cohortSql": "select 1", "testId": "%s"},
          "features": [],
          "dataAssets": [],
          "experimentDesign": {},
          "feasibility": {},
          "evidence": [],
          "declaredObservables": ["BOOKING_COMPLETED"],
          "notIncluded": ["trained model", "weights", "evaluation", "expected lift"],
          "clientId": "studio-client"
        }
        """
            .formatted(initiativeId, initiativeId);
    String hash = CandidatePackageHasher.hash(mapper.readTree(body), mapper);
    return body.replace("\"packageHash\": \"\"", "\"packageHash\": \"" + hash + "\"");
  }

  private String packageHash(String body) throws Exception {
    JsonNode json = mapper.readTree(body);
    return json.get("packageHash").asText();
  }
}
