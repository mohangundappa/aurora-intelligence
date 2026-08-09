package com.aurora.signals;

import com.aurora.common.SignalDefinition;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SignalLifecycleService {
  private final JdbcTemplate jdbc;
  private final SignalRegistry registry;

  public SignalLifecycleService(JdbcTemplate jdbc, SignalRegistry registry) {
    this.jdbc = jdbc;
    this.registry = registry;
  }

  public List<SignalLifecycle> list() {
    return registry.definitions().stream()
        .map(
            definition -> {
              ensure(definition);
              return jdbc.queryForObject(
                  "select signal_name,version,status,updated_at from signal_lifecycle where signal_name=?",
                  (result, row) ->
                      new SignalLifecycle(
                          result.getString("signal_name"),
                          result.getString("version"),
                          result.getString("status"),
                          result.getTimestamp("updated_at").toInstant()),
                  definition.name());
            })
        .toList();
  }

  public SignalLifecycle transition(String name, String status, String actor) {
    SignalDefinition definition = registry.definition(name);
    ensure(definition);
    String current =
        jdbc.queryForObject(
            "select status from signal_lifecycle where signal_name=?", String.class, name);
    jdbc.update(
        "update signal_lifecycle set status=?,version=?,updated_at=now() where signal_name=?",
        status,
        definition.version(),
        name);
    jdbc.update(
        """
        insert into signal_lifecycle_audit(
          signal_name,version,action,actor,from_status,to_status)
        values (?,?,?,?,?,?)
        """,
        name,
        definition.version(),
        status,
        actor,
        current,
        status);
    return list().stream().filter(item -> item.signalName().equals(name)).findFirst().orElseThrow();
  }

  public List<SignalAudit> audit(String name) {
    return jdbc.query(
        """
        select signal_name,version,action,actor,from_status,to_status,created_at
        from signal_lifecycle_audit where signal_name=? order by created_at
        """,
        (result, row) ->
            new SignalAudit(
                result.getString("signal_name"),
                result.getString("version"),
                result.getString("action"),
                result.getString("actor"),
                result.getString("from_status"),
                result.getString("to_status"),
                result.getTimestamp("created_at").toInstant()),
        name);
  }

  private void ensure(SignalDefinition definition) {
    jdbc.update(
        """
        insert into signal_lifecycle(signal_name,version,status)
        values (?,?,?) on conflict (signal_name) do nothing
        """,
        definition.name(),
        definition.version(),
        definition.lifecycleStatus().name());
  }

  public record SignalLifecycle(
      String signalName, String version, String status, java.time.Instant updatedAt) {}

  public record SignalAudit(
      String signalName,
      String version,
      String action,
      String actor,
      String fromStatus,
      String toStatus,
      java.time.Instant createdAt) {}
}
