package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.util.List;

public class TextAffinityCalculator extends CalculatorSupport {
  private final String signal;
  private final String token;
  private final String positive;
  private final String negative;

  public TextAffinityCalculator(String signal, String token, String positive, String negative) {
    this.signal = signal;
    this.token = token;
    this.positive = positive;
    this.negative = negative;
  }

  @Override
  public String name() {
    return signal;
  }

  @Override
  public SignalCalculation calculate(SignalDefinition definition, List<EventEnvelope> events) {
    boolean matched = contains(events, token);
    return new SignalCalculation(
        matched ? 75 + recency(definition, events) * 20 : 15,
        matched ? positive : negative,
        evidence(definition, events));
  }
}
