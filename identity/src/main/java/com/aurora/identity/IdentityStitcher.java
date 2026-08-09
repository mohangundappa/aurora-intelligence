package com.aurora.identity;

import com.aurora.cdp.CdpAdapter;
import com.aurora.common.EventEnvelope;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IdentityStitcher {
  private final CdpAdapter cdp;

  public IdentityStitcher(CdpAdapter cdp) {
    this.cdp = cdp;
  }

  public void process(EventEnvelope event) {
    if (!"CUSTOMER_IDENTIFIED".equals(event.eventName())) return;
    if (event.payload().get("customerId") == null) {
      throw new IllegalArgumentException("CUSTOMER_IDENTIFIED requires customerId");
    }
    cdp.linkIdentity(event);
  }

  public List<com.aurora.common.CdpProfile.IdentityLink> timeline(String anonymousId) {
    return cdp.profile(anonymousId).identityTimeline();
  }
}
