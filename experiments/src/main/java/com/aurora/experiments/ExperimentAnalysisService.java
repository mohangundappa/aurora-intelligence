package com.aurora.experiments;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExperimentAnalysisService {
  private final ExperimentAnalysisRepository analyses;
  private final ExperimentRegistry registry;

  public ExperimentAnalysisService(
      ExperimentAnalysisRepository analyses, ExperimentRegistry registry) {
    this.analyses = analyses;
    this.registry = registry;
  }

  public ExperimentAnalysis get(UUID analysisId) {
    return analyses
        .findById(analysisId)
        .orElseThrow(() -> new UnknownExperimentAnalysisException(analysisId));
  }

  public List<ExperimentAnalysis> list(String experimentId) {
    registry.definition(experimentId);
    return analyses.findByExperimentId(experimentId);
  }

  public void save(ExperimentAnalysis analysis) {
    registry.definition(analysis.experimentId());
    analyses.save(analysis);
  }
}
