package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CategoryTagResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private UUID createdBy;
    private String createdByDisplayName;
    private Integer usage;
    private Instant createdAt;
    private Instant updatedAt;

}
