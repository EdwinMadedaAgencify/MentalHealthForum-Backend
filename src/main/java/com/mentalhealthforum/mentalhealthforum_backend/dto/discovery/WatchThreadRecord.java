package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import java.time.Instant;
import java.util.UUID;

public record WatchThreadRecord(
    // Watch metadata
    UUID watch_id,
    Boolean notification_enabled,
    Instant watched_at,

    // Thread core info
    UUID thread_id,
    String thread_title,
    UUID creator_id,
    UUID category_id,

    // Thread type and status
    String thread_type,
    String thread_status,
    String content_warning_type,

    // Thread stats
    Integer post_count,
    Integer view_count,
    Instant last_activity_at,

    // Thread flags
    Boolean is_sticky,
    Boolean is_featured

) {}
