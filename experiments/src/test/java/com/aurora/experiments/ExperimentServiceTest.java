package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ExperimentServiceTest {
  @Test
  void missingVariantIsInsufficientEvenWhenOtherVariantHasExposure() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(
            contains("count(*) from experiment_exposures where experiment_id=? and variant=?"),
            eq(Integer.class),
            eq("exp"),
            eq("control")))
        .thenReturn(59);
    when(jdbc.queryForObject(
            contains("count(*) from experiment_exposures where experiment_id=? and variant=?"),
            eq(Integer.class),
            eq("exp"),
            eq("treatment")))
        .thenReturn(0);
    when(jdbc.queryForObject(
            contains("select count(*) from experiment_outcomes"),
            eq(Integer.class),
            any(),
            any(),
            any()))
        .thenReturn(0);

    ExperimentRegistry registry = mock(ExperimentRegistry.class);
    ExperimentDefinition definition =
        new ExperimentDefinition(
            "exp",
            "Experiment",
            "description",
            java.util.List.of(
                new ExperimentDefinition.Variant("control", 50),
                new ExperimentDefinition.Variant("treatment", 50)),
            "BOOKING_COMPLETED",
            30,
            ExperimentDefinition.LifecycleStatus.DEPLOYED);
    when(registry.definition("exp")).thenReturn(definition);

    ExperimentPerformance result = new ExperimentService(jdbc, registry).performance("exp");

    assertThat(result.variants())
        .extracting(ExperimentPerformance.Variant::name)
        .containsExactly("control", "treatment");
    assertThat(result.variants().get(0).exposed()).isEqualTo(59);
    assertThat(result.variants().get(1).exposed()).isZero();
    assertThat(result.insufficientSample()).isTrue();
  }

  @Test
  void nonDeployedExperimentDoesNotRecordExposureOrThrow() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ExperimentRegistry registry = mock(ExperimentRegistry.class);
    when(registry.definition("draft-experiment"))
        .thenReturn(definition(ExperimentDefinition.LifecycleStatus.DRAFT));

    new ExperimentService(jdbc, registry)
        .recordExposure(
            new Decision(
                "SHOW_DRAFT",
                "SHOW_DRAFT",
                "web",
                List.of(),
                "1.0",
                "draft-experiment",
                "draft",
                "session",
                "correlation"),
            new CdpProfile(
                "anonymous",
                null,
                new CdpProfile.Identity("anonymous", null, false),
                new CdpProfile.Loyalty("Guest", 0, false),
                new CdpProfile.ConsentState(true, true),
                java.util.Map.of(),
                java.util.Set.of(),
                List.of()));

    verifyNoInteractions(jdbc);
  }

  @Test
  void deployedExperimentRecordsExposure() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ExperimentRegistry registry = mock(ExperimentRegistry.class);
    when(registry.definition("deployed-experiment"))
        .thenReturn(
            definition(ExperimentDefinition.LifecycleStatus.DEPLOYED, "deployed-experiment"));

    new ExperimentService(jdbc, registry)
        .recordExposure(
            new Decision(
                "SHOW_DEPLOYED",
                "SHOW_DEPLOYED",
                "web",
                List.of(),
                "1.0",
                "deployed-experiment",
                "deployed",
                "session",
                "correlation"),
            new CdpProfile(
                "anonymous",
                null,
                new CdpProfile.Identity("anonymous", null, false),
                new CdpProfile.Loyalty("Guest", 0, false),
                new CdpProfile.ConsentState(true, true),
                java.util.Map.of(),
                java.util.Set.of(),
                List.of()));

    verify(jdbc).update(anyString(), any(), any(), any(), any(), any());
  }

  @Test
  void nonDeployedPerformanceWithholdsLift() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(0);
    ExperimentRegistry registry = mock(ExperimentRegistry.class);
    when(registry.definition("draft-experiment"))
        .thenReturn(definition(ExperimentDefinition.LifecycleStatus.DRAFT));

    ExperimentPerformance result =
        new ExperimentService(jdbc, registry).performance("draft-experiment");

    assertThat(result.insufficientSample()).isTrue();
    assertThat(result.warning()).contains("not serving", "lift is withheld");
    assertThat(result.variants())
        .extracting(ExperimentPerformance.Variant::exposed)
        .containsOnly(0);
  }

  private ExperimentDefinition definition(ExperimentDefinition.LifecycleStatus status) {
    return definition(status, "draft-experiment");
  }

  private ExperimentDefinition definition(ExperimentDefinition.LifecycleStatus status, String id) {
    return new ExperimentDefinition(
        id,
        "Experiment",
        "description",
        List.of(
            new ExperimentDefinition.Variant("control", 50),
            new ExperimentDefinition.Variant("treatment", 50)),
        "BOOKING_COMPLETED",
        30,
        status);
  }
}
