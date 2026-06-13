package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class FocusCategoryResponse {

    // Focus metadata
    private UUID id;
    private Boolean notificationEnabled;
    private Instant focusedAt;

    // Category core info
    private UUID categoryId;
    private String categorySlug;
    private String categoryName;
    private String categoryDescription;
    private String colorTheme;

    // Category metadata (augmented)
    private UUID parentCategoryId;
    private ContentWarningType contentWarningType;
    private Integer threadCount; // How many threads in this category

    // Helpful flags
    private Boolean isParent;
    private Boolean isChild;

}
