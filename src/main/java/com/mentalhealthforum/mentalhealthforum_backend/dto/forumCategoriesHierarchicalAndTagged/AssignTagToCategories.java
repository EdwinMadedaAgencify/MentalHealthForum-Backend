package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AssignTagToCategories(
    @NotEmpty(message = "At least one category Id is required")
    @Size(max = 20, message = "Cannot assign to more than 20 categories at once")
    List<UUID> categoryIds
) {}
