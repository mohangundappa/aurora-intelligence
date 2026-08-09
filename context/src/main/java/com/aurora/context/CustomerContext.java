package com.aurora.context;
import com.aurora.common.*;
public record CustomerContext(String sessionId, SignalResult signal, java.util.Map<String,Object> profile) {}
