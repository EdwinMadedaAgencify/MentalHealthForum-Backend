package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;

public record ForumCategoryTagRequest(
    @NotBlank(message = "Tag name is required")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Tag name must contain only lowercase letters, numbers, and hyphens")
    @Length(min = 2, max = 50, message = "Tag name must be between 2 and 50 characters")
   String name,

    @Length(max = 255, message = "Description cannot exceed 255  characters")
   String description
) {}
