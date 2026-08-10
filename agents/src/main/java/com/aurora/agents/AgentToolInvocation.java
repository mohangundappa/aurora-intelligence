package com.aurora.agents;

import java.util.UUID;

public record AgentToolInvocation(
    UUID callId, String toolName, String resultReference, String status, Object result) {}
