package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import java.util.UUID;

/**
 * Record for bookmark status batch result
 */
public record BookmarkStatusRecord(
    UUID thread_id,
    Boolean is_bookmarked
) {}
