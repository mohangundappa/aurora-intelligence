package com.aurora.experiments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ExperimentDefinitionRepository {
  private final JdbcTemplate jdbc;

  public ExperimentDefinitionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ExperimentDefinition> findAll() {
    List<ExperimentDefinitionRow> rows =
        jdbc.query(
            """
            select experiment_id,name,description,primary_outcome_event,
                   minimum_exposures_per_variant,lifecycle_status
            from experiment_definitions order by experiment_id
            """,
            (result, row) ->
                new ExperimentDefinitionRow(
                    result.getString("experiment_id"),
                    result.getString("name"),
                    result.getString("description"),
                    result.getString("primary_outcome_event"),
                    result.getInt("minimum_exposures_per_variant"),
                    result.getString("lifecycle_status")));
    Map<String, ExperimentDefinitionRow> definitions = new LinkedHashMap<>();
    rows.forEach(row -> definitions.put(row.id(), row));
    List<VariantRow> variants =
        jdbc.query(
            """
            select experiment_id,variant_name,allocation_percentage,variant_order
            from experiment_definition_variants
            order by experiment_id,variant_order
            """,
            (result, row) ->
                new VariantRow(
                    result.getString("experiment_id"),
                    result.getString("variant_name"),
                    result.getInt("allocation_percentage"),
                    result.getInt("variant_order")));
    Map<String, List<ExperimentDefinition.Variant>> variantsByDefinition = new LinkedHashMap<>();
    variants.forEach(
        variant ->
            variantsByDefinition
                .computeIfAbsent(variant.experimentId(), ignored -> new ArrayList<>())
                .add(
                    variant.ordinal(),
                    new ExperimentDefinition.Variant(variant.name(), variant.allocation())));
    variantsByDefinition.keySet().stream()
        .filter(id -> !definitions.containsKey(id))
        .findFirst()
        .ifPresent(
            id -> {
              throw new IllegalStateException(
                  "Experiment definition variants reference unknown experiment " + id);
            });
    return definitions.values().stream()
        .map(
            row ->
                new ExperimentDefinition(
                    row.id(),
                    row.name(),
                    row.description(),
                    variantsByDefinition.getOrDefault(row.id(), List.of()),
                    row.primaryOutcomeEvent(),
                    row.minimumExposures(),
                    ExperimentDefinition.LifecycleStatus.valueOf(row.lifecycleStatus())))
        .toList();
  }

  @Transactional
  public void save(ExperimentDefinition definition) {
    jdbc.update(
        """
        insert into experiment_definitions(
          experiment_id,name,description,primary_outcome_event,
          minimum_exposures_per_variant,lifecycle_status)
        values (?,?,?,?,?,?)
        """,
        definition.id(),
        definition.name(),
        definition.description(),
        definition.primaryOutcomeEvent(),
        definition.minimumExposuresPerVariant(),
        definition.lifecycleStatus().name());
    for (int ordinal = 0; ordinal < definition.variants().size(); ordinal++) {
      ExperimentDefinition.Variant variant = definition.variants().get(ordinal);
      jdbc.update(
          """
          insert into experiment_definition_variants(
            experiment_id,variant_name,allocation_percentage,variant_order)
          values (?,?,?,?)
          """,
          definition.id(),
          variant.name(),
          variant.allocationPercentage(),
          ordinal);
    }
  }

  public void transitionLifecycle(
      String experimentId,
      ExperimentDefinition.LifecycleStatus from,
      ExperimentDefinition.LifecycleStatus to) {
    int updated =
        jdbc.update(
            "update experiment_definitions set lifecycle_status=?,updated_at=now() "
                + "where experiment_id=? and lifecycle_status=?",
            to.name(),
            experimentId,
            from.name());
    if (updated != 1) {
      throw new IllegalStateException("Experiment lifecycle changed while processing deployment");
    }
  }

  public record ExperimentDefinitionRow(
      String id,
      String name,
      String description,
      String primaryOutcomeEvent,
      int minimumExposures,
      String lifecycleStatus) {}

  public record VariantRow(String experimentId, String name, int allocation, int ordinal) {}
}
