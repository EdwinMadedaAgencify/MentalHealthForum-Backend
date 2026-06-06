package com.mentalhealthforum.mentalhealthforum_backend.dto.threadLifecycleAndMetadata;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadStatus;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadResponse {
    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private String categorySlug;

    private String title;

    private UUID creatorId;
    private String creatorDisplayName;
    private String creatorAvatarUrl;

    private ThreadType threadType;
    private ThreadStatus threadStatus;

    private ContentWarningType contentWarningType;
    private String contentWarningCustomText;

    private List<String> tags;
    private boolean isSticky;
    private boolean isFeatured;
    private boolean isBookmarked;

    private Integer bookmarkCount;
    private Integer postCount;
    private Integer viewCount;

    private UUID bestAnswerPostId;
    private Instant resolvedAt;
    private UUID resolvedByUserId;

    // Lock metadata
    private String lockReason;
    private UUID lockedBy;
    private Instant lockedAt;
    private Instant lockExpiresAt;

    // Edit metadata
    private Instant lastEditedAt;

    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastActivityAt;

}
