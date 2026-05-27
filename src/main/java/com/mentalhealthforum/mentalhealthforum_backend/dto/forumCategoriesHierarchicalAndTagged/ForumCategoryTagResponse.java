package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ForumCategoryTagResponse {
    private UUID id;
    private String name;
    private String description;
}
