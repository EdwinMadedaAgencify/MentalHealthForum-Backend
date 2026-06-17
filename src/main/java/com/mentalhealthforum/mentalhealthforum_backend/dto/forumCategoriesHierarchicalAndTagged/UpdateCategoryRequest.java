package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
public class UpdateCategoryRequest {

    @Length(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String name;

    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    @Length(max = 100, message = "Slug cannot exceed 100 characters")
    private String slug; //Made optional

    @Length(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Pattern(regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$", message = "Color must be a valid Hex color (e.g., #3B82F6 or #3BF)")
    private String colorTheme;

    private UUID parentCategoryId;

    private ContentWarningType contentWarningType;

    private String contentWarningCustomText;

    @PositiveOrZero(message = "Sort order must be zero or positive")
    private Integer sortOrder;

    private Boolean isActive;

    @Builder.Default
    @Size(max = 3, message = "Maximum 3 tags per category")
    private List<UUID> tagIds = new ArrayList<>(); // Just IDs, no embedded creation
}
