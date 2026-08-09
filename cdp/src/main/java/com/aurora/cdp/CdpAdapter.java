package com.aurora.cdp;

import com.aurora.common.EventEnvelope;

public interface CdpAdapter {
  void accept(EventEnvelope event);
}
