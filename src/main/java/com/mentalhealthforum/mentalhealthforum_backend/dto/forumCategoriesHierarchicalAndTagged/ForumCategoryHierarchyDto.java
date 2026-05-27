package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryTagEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryEntity;

import java.util.List;

public record ForumCategoryHierarchyDto(
        ForumCategoryResponse category,
        List<ForumCategoryResponse> children,
        List<ForumCategoryTagResponse> tags
) {}
