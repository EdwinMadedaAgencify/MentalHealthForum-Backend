package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import java.time.Instant;
import java.util.UUID;

public record BookmarkedThreadRecord(
    UUID bookmark_id,
    UUID thread_id,
    String title,
    UUID creator_id,
    Integer post_count,
    Integer view_count,
    Instant last_activity_at,
    Instant bookmarked_at,
    String bookmark_notes
) {}
