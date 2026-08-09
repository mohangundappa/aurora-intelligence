package com.aurora.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelServiceTest {
  @Test
  void predictionIncludesDeployedVersionAndFeatureContributions() {
    ModelRepository repository = mock(ModelRepository.class);
    when(repository.findDeployed("booking-intent"))
        .thenReturn(
            Optional.of(
                new ModelVersion(
                    "booking-intent",
                    "3.0",
                    "DEPLOYED",
                    List.of("propertyViewed", "bookingStarted"),
                    Map.of("propertyViewed", 10d, "bookingStarted", 20d),
                    5d)));

    Prediction prediction =
        new ModelService(repository)
            .predict("booking-intent", Map.of("propertyViewed", 2d, "bookingStarted", 1d));

    assertThat(prediction.modelVersion()).isEqualTo("3.0");
    assertThat(prediction.score()).isEqualTo(45d);
    assertThat(prediction.contributions())
        .containsEntry("propertyViewed", 20d)
        .containsEntry("bookingStarted", 20d);
    assertThat(prediction.explanation()).contains("version 3.0");
  }

  @Test
  void lifecycleOperationsDelegateWithActor() {
    ModelRepository repository = mock(ModelRepository.class);
    ModelService service = new ModelService(repository);

    service.approve("booking-intent", "2.0", "tester");
    service.deploy("booking-intent", "2.0", "tester");
    service.rollback("booking-intent", "1.0", "tester");

    verify(repository).transition("booking-intent", "2.0", "APPROVED", "tester");
    verify(repository).transition("booking-intent", "2.0", "DEPLOYED", "tester");
    verify(repository).transition("booking-intent", "1.0", "DEPLOYED", "tester");
  }
}
