package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateForumCategoryRequest{
    @NotBlank(message = "Category name is required")
    @Length(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String name;

    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    @Length(max = 100, message = "Slug cannot exceed 100 characters")
    private String slug; //Made optional

    @Length(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Pattern(regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$", message = "Color must be a valid Hex color (e.g., #3B82F6 or #3BF)")
    @Builder.Default
    private String colorTheme = "#3B82F6";

    private UUID parentCategoryId;

    @Builder.Default
    private ContentWarningType contentWarningType = ContentWarningType.NONE;

    private String contentWarningCustomText;

    @Builder.Default
    @PositiveOrZero(message = "Sort order must be zero or positive")
    private Integer sortOrder = 0;

    @Builder.Default
    private List<ForumCategoryTagRequest> tags = new ArrayList<>();
}
