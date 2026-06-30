package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import java.time.Instant;
import java.util.UUID;

public record GlobalSearchResult(
    UUID entityId,
    String entityType,
    String header,
    String bodyPreview,
    Double searchScore,
    Instant last_activity_at
) {}
