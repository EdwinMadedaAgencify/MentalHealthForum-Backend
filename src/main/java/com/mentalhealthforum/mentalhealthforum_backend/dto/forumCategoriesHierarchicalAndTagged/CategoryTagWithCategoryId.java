package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import java.time.Instant;
import java.util.UUID;

/**
 * Record for batch tag result containing category ID and tag details
 */
public record CategoryTagWithCategoryId(
    UUID category_id,
    UUID id,
    String name,
    String slug,
    String description,
    UUID created_by,
    Instant created_at,
    Instant updated_at
) {}
