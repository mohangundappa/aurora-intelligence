package com.aurora.ingest;

import java.util.List;
import java.util.UUID;

public record IngestResult(
    int accepted,
    int duplicates,
    int quarantined,
    List<UUID> acceptedEventIds,
    List<UUID> quarantinedEventIds) {}
