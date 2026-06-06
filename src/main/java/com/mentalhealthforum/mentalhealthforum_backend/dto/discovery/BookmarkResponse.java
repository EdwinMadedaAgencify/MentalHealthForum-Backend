package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BookmarkResponse {

    private UUID id;
    private UUID threadId;
    private String threadTitle;
    private UUID threadCreatorId;
    private String threadCreatorDisplayName;

    private Integer threadPostCount;
    private Integer threadViewCount;
    private Instant threadLastActivityAt;

    private String notes;
    private Instant bookmarkedAt;

}
