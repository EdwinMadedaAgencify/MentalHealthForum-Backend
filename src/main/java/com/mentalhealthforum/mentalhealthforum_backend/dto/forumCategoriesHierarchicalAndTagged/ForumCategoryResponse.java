package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ForumCategoryResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String colorTheme;
    private UUID parentCategoryId;
    private ContentWarningType contentWarningType;
    private String contentWarningCustomText;
    private Boolean isActive;
    private Integer sortOrder;
    private Instant createdAt;

    // Computed fields
    private Boolean isParent;
    private Boolean isChild;

    // Tags (flattened)
    private List<ForumCategoryTagResponse> tags;
}
