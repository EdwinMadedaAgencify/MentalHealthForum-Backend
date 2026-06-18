package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import java.util.UUID;

/**
 * Record for watch status batch result
 */
public record WatchStatusRecord(
    UUID thread_id,
    Boolean is_watched
) {}
