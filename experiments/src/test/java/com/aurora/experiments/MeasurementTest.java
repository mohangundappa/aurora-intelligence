package com.aurora.experiments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MeasurementTest {
  @Test
  void sufficientSamplesComputeAbsoluteAndRelativeLift() {
    Measurement.Result result = Measurement.conversion(40, 12, 40, 8);

    assertThat(result.insufficientSample()).isFalse();
    assertThat(result.absoluteLift()).isCloseTo(0.1, org.assertj.core.data.Offset.offset(0.0001));
    assertThat(result.relativeLift()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.0001));
  }
}
