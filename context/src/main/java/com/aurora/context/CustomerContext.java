package com.aurora.context;

import com.aurora.common.CdpProfile;
import com.aurora.common.Decision;
import com.aurora.common.EventEnvelope;
import com.aurora.common.SignalSnapshot;
import java.util.List;

public record CustomerContext(
    CdpProfile profile,
    String sessionId,
    List<EventEnvelope> recentBehaviors,
    List<SignalSnapshot> activeSignals,
    String journeyStage,
    boolean personalizationEligible,
    Decision recommendedAction) {}
