package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import java.util.UUID;

/**
 * Record for thread count status batch result
 */
public record ThreadCountRecord(
    UUID category_id,
    Long count
) {}
