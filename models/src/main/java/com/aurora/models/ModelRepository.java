package com.aurora.models;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ModelRepository {
  private static final TypeReference<List<String>> FEATURES = new TypeReference<>() {};
  private static final TypeReference<Map<String, Double>> WEIGHTS = new TypeReference<>() {};
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public ModelRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public ModelVersion findDeployed(String name) {
    return jdbc.queryForObject(
        "select model_name,version,status,features,weights,bias from model_versions where model_name=? and status='DEPLOYED' order by deployed_at desc limit 1",
        (result, row) -> toModel(result),
        name);
  }

  public List<ModelVersion> findAll(String name) {
    return jdbc.query(
        "select model_name,version,status,features,weights,bias from model_versions where model_name=? order by version",
        (result, row) -> toModel(result),
        name);
  }

  public void transition(String name, String version, String status, String actor) {
    String previous =
        jdbc
            .query(
                "select version from model_versions where model_name=? and status='DEPLOYED' limit 1",
                (result, row) -> result.getString("version"),
                name)
            .stream()
            .findFirst()
            .orElse(null);
    jdbc.update(
        "update model_versions set status=? where model_name=? and version=?",
        status,
        name,
        version);
    if ("DEPLOYED".equals(status)) {
      jdbc.update(
          "update model_versions set status='APPROVED' where model_name=? and status='DEPLOYED' and version<>?",
          name,
          version);
      jdbc.update(
          "update model_versions set deployed_at=now() where model_name=? and version=?",
          name,
          version);
    }
    jdbc.update(
        "insert into model_audit(model_name,version,action,actor,from_version,to_version) values (?,?,?,?,?,?)",
        name,
        version,
        status,
        actor,
        previous,
        version);
  }

  public List<Map<String, Object>> audit(String name) {
    return jdbc.queryForList(
        "select version,action,actor,from_version,to_version,created_at from model_audit where model_name=? order by created_at desc",
        name);
  }

  private ModelVersion toModel(ResultSet result) {
    try {
      return new ModelVersion(
          result.getString("model_name"),
          result.getString("version"),
          result.getString("status"),
          mapper.readValue(result.getString("features"), FEATURES),
          mapper.readValue(result.getString("weights"), WEIGHTS),
          result.getDouble("bias"));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read model version", exception);
    }
  }
}
