package com.aurora.signals;

import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalDefinition;
import java.util.List;

public interface SignalCalculator {
  String name();

  SignalCalculation calculate(SignalDefinition definition, List<EventEnvelope> events);
}
