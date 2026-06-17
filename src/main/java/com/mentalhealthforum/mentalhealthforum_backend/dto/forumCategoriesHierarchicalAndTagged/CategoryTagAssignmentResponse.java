package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CategoryTagAssignmentResponse {

    private UUID categoryId;
    private String categoryName;
    private UUID tagId;
    private String tagName;
    private String tagDescription;
    private UUID assignedBy;
    private String assignedByDisplayName;
    private Instant assignedAt;

}
