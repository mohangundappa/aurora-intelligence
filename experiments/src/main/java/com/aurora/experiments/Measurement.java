package com.aurora.experiments;

public final class Measurement {
  private Measurement() {}

  public static Result conversion(
      long exposed, long converted, long controlExposed, long controlConverted) {
    if (exposed < 30 || controlExposed < 30) {
      return new Result(
          rate(converted, exposed),
          rate(controlConverted, controlExposed),
          0,
          0,
          true,
          "Insufficient sample size; significance and lift are not claimable.");
    }
    double treatmentRate = rate(converted, exposed);
    double controlRate = rate(controlConverted, controlExposed);
    return new Result(
        treatmentRate,
        controlRate,
        treatmentRate - controlRate,
        controlRate == 0 ? 0 : (treatmentRate - controlRate) / controlRate,
        false,
        "Sample size is sufficient for directional lift reporting.");
  }

  private static double rate(long converted, long exposed) {
    return exposed == 0 ? 0 : (double) converted / exposed;
  }

  public record Result(
      double treatmentRate,
      double controlRate,
      double absoluteLift,
      double relativeLift,
      boolean insufficientSample,
      String warning) {}
}
