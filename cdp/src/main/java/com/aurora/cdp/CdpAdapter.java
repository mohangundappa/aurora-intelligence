package com.aurora.cdp;

import com.aurora.common.CdpProfile;
import com.aurora.common.EventEnvelope;

public interface CdpAdapter {
  void accept(EventEnvelope event);

  CdpProfile profile(String anonymousId);

  void linkIdentity(EventEnvelope event);
}
