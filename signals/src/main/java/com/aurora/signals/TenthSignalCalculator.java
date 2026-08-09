package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TenthSignalCalculator extends CalculatorSupport {
  @Override
  public String name() {
    return "template-signal";
  }

  @Override
  public SignalCalculation calculate(SignalDefinition definition, List<EventEnvelope> events) {
    long evidence = evidence(definition, events);
    return new SignalCalculation(
        Math.min(100, evidence * 20),
        definition.explanationTemplate().replace("{evidenceCount}", String.valueOf(evidence)),
        evidence);
  }
}
