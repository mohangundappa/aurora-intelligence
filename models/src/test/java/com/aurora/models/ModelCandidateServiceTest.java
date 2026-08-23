package com.aurora.models;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ModelCandidateServiceTest {
  @Test
  void refusesRegistrationWhenStudioTokenIsUnconfigured() {
    ModelCandidateService service =
        new ModelCandidateService(mock(ModelCandidateRepository.class), new ObjectMapper(), "");

    assertThatThrownBy(
            () ->
                service.register(
                    "booking-intent", null, null, new ObjectMapper().createObjectNode()))
        .isInstanceOf(CandidateTokenNotConfiguredException.class)
        .hasMessage("Candidate registration token is not configured");
  }
}
