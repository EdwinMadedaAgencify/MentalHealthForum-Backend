package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import java.util.UUID;

/**
 * Record for bookmark count status batch result
 */
public record BookmarkCountRecord(
    UUID thread_id,
    Long count
) {}
