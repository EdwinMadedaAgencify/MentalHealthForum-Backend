package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import java.util.UUID;

public record TagUsageRecord(UUID tag_id, Long count) {}
