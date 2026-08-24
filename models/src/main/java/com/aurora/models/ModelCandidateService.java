package com.aurora.models;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelCandidateService {
  private static final Set<String> ALLOWED_FIELDS =
      Set.of(
          "studioInitiativeId",
          "requirementId",
          "packageHash",
          "modelName",
          "targeting",
          "features",
          "dataAssets",
          "experimentDesign",
          "feasibility",
          "evidence",
          "declaredObservables",
          "notIncluded",
          "clientId");
  private final ModelCandidateRepository repository;
  private final com.fasterxml.jackson.databind.ObjectMapper mapper;
  private final String studioToken;

  public ModelCandidateService(
      ModelCandidateRepository repository,
      com.fasterxml.jackson.databind.ObjectMapper mapper,
      @Value("${aurora.candidates.studio-token:}") String studioToken) {
    this.repository = repository;
    this.mapper = mapper;
    this.studioToken = studioToken;
  }

  @Transactional
  public CandidateRegistration register(
      String pathModelName, String idempotencyKey, String requestToken, JsonNode body) {
    authenticate(requestToken);
    if (body == null || !body.isObject()) {
      throw new InvalidCandidateException("candidate body must be a JSON object");
    }
    Set<String> unknown = new TreeSet<>();
    body.fieldNames()
        .forEachRemaining(
            field -> {
              if (!ALLOWED_FIELDS.contains(field)) unknown.add(field);
            });
    if (!unknown.isEmpty()) {
      String prefix =
          unknown.size() == 1 ? "Unknown candidate field: " : "Unknown candidate fields: ";
      throw new InvalidCandidateException(prefix + String.join(", ", unknown));
    }
    String modelName = requiredText(body, "modelName");
    if (!modelName.equals(pathModelName)) {
      throw new InvalidCandidateException("modelName must match the path model name");
    }
    String packageHash = requiredText(body, "packageHash");
    if (idempotencyKey != null && !idempotencyKey.equals(packageHash)) {
      throw new InvalidCandidateException("Idempotency-Key must match packageHash");
    }
    String studioInitiativeId = requiredText(body, "studioInitiativeId");
    String clientId = requiredText(body, "clientId");
    if (!packageHash.equals(CandidatePackageHasher.hash(body, mapper))) {
      throw new InvalidCandidateException("packageHash does not match candidate package content");
    }
    Map<String, Object> packageContent =
        mapper.convertValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    ModelCandidateRepository.RegistrationResult result =
        repository.register(
            UUID.randomUUID(),
            modelName,
            packageHash,
            studioInitiativeId,
            clientId,
            packageContent);
    return new CandidateRegistration(result.candidate().candidateId(), result.candidate().status());
  }

  public List<ModelCandidate> candidates(String modelName, String requestToken) {
    authenticate(requestToken);
    return repository.findAll(modelName);
  }

  private void authenticate(String requestToken) {
    if (studioToken == null || studioToken.isBlank()) {
      throw new CandidateTokenNotConfiguredException(
          "Candidate registration token is not configured");
    }
    byte[] configuredToken = studioToken.getBytes(StandardCharsets.UTF_8);
    byte[] suppliedToken =
        requestToken == null ? new byte[0] : requestToken.getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(configuredToken, suppliedToken)) {
      throw new InvalidCandidateTokenException("Invalid candidate registration token");
    }
  }

  private String requiredText(JsonNode body, String field) {
    JsonNode value = body.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw new InvalidCandidateException(field + " is required");
    }
    return value.asText();
  }
}
