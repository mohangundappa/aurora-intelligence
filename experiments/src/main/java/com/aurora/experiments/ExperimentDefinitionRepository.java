package com.aurora.experiments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
            select experiment_id,variant_name,allocation_percentage
            from experiment_definition_variants
            order by experiment_id,variant_name
            """,
            (result, row) ->
                new VariantRow(
                    result.getString("experiment_id"),
                    result.getString("variant_name"),
                    result.getInt("allocation_percentage")));
    Map<String, List<ExperimentDefinition.Variant>> variantsByDefinition = new LinkedHashMap<>();
    variants.forEach(
        variant ->
            variantsByDefinition
                .computeIfAbsent(variant.experimentId(), ignored -> new ArrayList<>())
                .add(new ExperimentDefinition.Variant(variant.name(), variant.allocation())));
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
    definition
        .variants()
        .forEach(
            variant ->
                jdbc.update(
                    """
                    insert into experiment_definition_variants(
                      experiment_id,variant_name,allocation_percentage)
                    values (?,?,?)
                    """,
                    definition.id(),
                    variant.name(),
                    variant.allocationPercentage()));
  }

  public record ExperimentDefinitionRow(
      String id,
      String name,
      String description,
      String primaryOutcomeEvent,
      int minimumExposures,
      String lifecycleStatus) {}

  public record VariantRow(String experimentId, String name, int allocation) {}
}
