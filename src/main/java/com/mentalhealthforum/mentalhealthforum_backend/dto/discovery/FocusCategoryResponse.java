package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class FocusCategoryResponse {

    private UUID id;
    private UUID categoryId;
    private String categorySlug;
    private String categoryName;
    private String categoryDescription;
    private Boolean notificationEnabled;
    private Instant focusedAt;

}
