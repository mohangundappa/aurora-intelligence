package com.aurora.experiments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    registry.refreshAfterWrite(definition.id());
  }

  public void saveAfterCommit(ExperimentDefinition definition) {
    registry.assertCanWrite(definition.id());
    repository.save(definition);
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      registry.refreshAfterWrite(definition.id());
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            registry.refreshAfterWrite(definition.id());
          }
        });
  }
}
