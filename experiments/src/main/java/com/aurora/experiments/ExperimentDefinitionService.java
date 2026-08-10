package com.aurora.experiments;

import org.springframework.stereotype.Service;

@Service
public class ExperimentDefinitionService {
  private final ExperimentDefinitionRepository repository;
  private final ExperimentRegistry registry;

  public ExperimentDefinitionService(
      ExperimentDefinitionRepository repository, ExperimentRegistry registry) {
    this.repository = repository;
    this.registry = registry;
  }

  public void save(ExperimentDefinition definition) {
    registry.assertCanWrite(definition.id());
    repository.save(definition);
    registry.refresh();
  }
}
