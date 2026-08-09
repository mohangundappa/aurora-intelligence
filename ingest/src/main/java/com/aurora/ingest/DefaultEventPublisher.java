package com.aurora.ingest;

import com.aurora.cdp.SimulatedCdpAdapter;
import com.aurora.common.EventEnvelope;
import com.aurora.signals.SignalEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DefaultEventPublisher implements EventPublisher {
  private final SimulatedCdpAdapter cdp;
  private final SignalEngine signals;

  public DefaultEventPublisher(SimulatedCdpAdapter cdp, SignalEngine signals) {
    this.cdp = cdp;
    this.signals = signals;
  }

  public void publish(EventEnvelope event) {
    cdp.accept(event);
    signals.accept(event);
  }
}
