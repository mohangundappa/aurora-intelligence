package com.aurora.models;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ModelCandidateRepository {
  private static final TypeReference<Map<String, Object>> PACKAGE = new TypeReference<>() {};
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public ModelCandidateRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public RegistrationResult register(
      UUID candidateId,
      String modelName,
      String packageHash,
      String studioInitiativeId,
      Map<String, Object> packageContent) {
    List<UUID> inserted =
        jdbc
            .query(
                """
                insert into model_candidates(
                  candidate_id,model_name,package_hash,studio_initiative_id,package,status)
                values (?,?,?,?,?::jsonb,'AWAITING_WEIGHTS')
                on conflict (model_name,package_hash) do nothing
                returning candidate_id
                """,
                (result, row) -> UUID.fromString(result.getString("candidate_id")),
                candidateId,
                modelName,
                packageHash,
                studioInitiativeId,
                write(packageContent))
            .stream()
            .toList();
    ModelCandidate candidate =
        inserted.isEmpty()
            ? find(modelName, packageHash)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Candidate disappeared after idempotent registration"))
            : findById(inserted.get(0))
                .orElseThrow(
                    () -> new IllegalStateException("Candidate disappeared after registration"));
    jdbc.update(
        "insert into model_candidate_audit(candidate_id,model_name,package_hash,action) values (?,?,?,?)",
        candidate.candidateId(),
        candidate.modelName(),
        candidate.packageHash(),
        inserted.isEmpty() ? "REPLAYED" : "REGISTERED");
    return new RegistrationResult(candidate, inserted.isEmpty());
  }

  public List<ModelCandidate> findAll(String modelName) {
    return jdbc.query(
        """
        select candidate_id,model_name,package_hash,studio_initiative_id,status,package,created_at
        from model_candidates
        where model_name=?
        order by created_at
        """,
        (result, row) -> toCandidate(result),
        modelName);
  }

  private Optional<ModelCandidate> find(String modelName, String packageHash) {
    return jdbc
        .query(
            """
            select candidate_id,model_name,package_hash,studio_initiative_id,status,package,created_at
            from model_candidates
            where model_name=? and package_hash=?
            """,
            (result, row) -> toCandidate(result),
            modelName,
            packageHash)
        .stream()
        .findFirst();
  }

  private Optional<ModelCandidate> findById(UUID candidateId) {
    return jdbc
        .query(
            """
            select candidate_id,model_name,package_hash,studio_initiative_id,status,package,created_at
            from model_candidates
            where candidate_id=?
            """,
            (result, row) -> toCandidate(result),
            candidateId)
        .stream()
        .findFirst();
  }

  private ModelCandidate toCandidate(ResultSet result) {
    try {
      return new ModelCandidate(
          result.getObject("candidate_id", UUID.class),
          result.getString("model_name"),
          result.getString("package_hash"),
          result.getString("studio_initiative_id"),
          result.getString("status"),
          mapper.readValue(result.getString("package"), PACKAGE),
          result.getTimestamp("created_at").toInstant());
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read model candidate", exception);
    }
  }

  private String write(Map<String, Object> packageContent) {
    try {
      return mapper.writeValueAsString(packageContent);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to write model candidate package", exception);
    }
  }

  public record RegistrationResult(ModelCandidate candidate, boolean replay) {}
}
