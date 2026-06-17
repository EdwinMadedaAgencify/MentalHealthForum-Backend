package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import java.util.List;

public record CategoryHierarchyDto(
        CategoryResponse category,
        List<CategoryResponse> children,
        List<CategoryTagResponse> tags
) {}
