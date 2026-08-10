package com.aurora.objectives;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class MarketingObjectiveServiceTest {
  @Test
  void objectiveValidationRejectsInvalidDateRange() {
    assertThatThrownBy(
            () ->
                new MarketingObjective(
                    "objective-1",
                    "Increase bookings",
                    "Description",
                    "Grow direct bookings",
                    "BOOKING_COMPLETED",
                    BigDecimal.TEN,
                    "Miami families",
                    Map.of(),
                    LocalDate.of(2026, 2, 1),
                    LocalDate.of(2026, 1, 1),
                    MarketingObjective.Status.DRAFT,
                    "marketer",
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("valid range");
  }

  @Test
  void illegalLifecycleTransitionIsRejected() {
    MarketingObjectiveRepository repository = mock(MarketingObjectiveRepository.class);
    MarketingObjective objective = objective(MarketingObjective.Status.DRAFT);
    when(repository.findById("objective-1")).thenReturn(Optional.of(objective));

    MarketingObjectiveService service = new MarketingObjectiveService(repository);

    assertThatThrownBy(
            () ->
                service.transition("objective-1", MarketingObjective.Status.COMPLETED, "presenter"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("DRAFT -> COMPLETED");
  }

  @Test
  void legalLifecycleTransitionIsAudited() {
    MarketingObjectiveRepository repository = mock(MarketingObjectiveRepository.class);
    MarketingObjective objective = objective(MarketingObjective.Status.DRAFT);
    MarketingObjective active = objective(MarketingObjective.Status.ACTIVE);
    when(repository.findById("objective-1"))
        .thenReturn(Optional.of(objective), Optional.of(active));

    new MarketingObjectiveService(repository)
        .transition("objective-1", MarketingObjective.Status.ACTIVE, "presenter");

    verify(repository).transition("objective-1", MarketingObjective.Status.ACTIVE);
    verify(repository)
        .audit(
            "objective-1",
            "presenter",
            MarketingObjective.Status.DRAFT,
            MarketingObjective.Status.ACTIVE);
  }

  private MarketingObjective objective(MarketingObjective.Status status) {
    return new MarketingObjective(
        "objective-1",
        "Increase bookings",
        "Description",
        "Grow direct bookings",
        "BOOKING_COMPLETED",
        BigDecimal.TEN,
        "Miami families",
        Map.of(),
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 2, 1),
        status,
        "marketer",
        Instant.now());
  }
}
