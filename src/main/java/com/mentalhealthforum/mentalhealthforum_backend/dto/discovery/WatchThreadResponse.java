package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadStatus;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WatchThreadResponse {
    // Watch metadata
    private UUID id;
    private Boolean notificationEnabled;
    private Instant watchedAt;

    // Thread core info
    private UUID threadId;
    private String threadTitle;
    private ThreadType threadType;
    private ThreadStatus threadStatus;

    // Thread metadata
    private UUID categoryId;
    private UUID creatorId;
    private String creatorDisplayName;
    private String creatorAvatarUrl;

    // Quick context (most valuable for users)
    private Integer postCount;
    private Integer viewCount;
    private Instant lastActivityAt;

    // Content warnings
    private ContentWarningType contentWarningType;

    // Thread settings
    private Boolean isOpen;

    // User-specific
    private Boolean isBookmarked;

    // Thread flags
    private Boolean isSticky;
    private Boolean isFeatured;

}
