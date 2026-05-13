package com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.EditReason;
import com.mentalhealthforum.mentalhealthforum_backend.enums.PostType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {

    private UUID id;
    private UUID threadId;
    private UUID parentPostId;

    private UUID authorId;
    private String authorDisplayName;
    private String authorAvatarUrl;
    private String anonymousIdentifier;

    private PostType postType;
    private String content;
    private Integer wordCount;

    private ContentWarningType contentWarningType;
    private String contentWarningCustomText;

    private boolean isFlaggedForReview;
    private boolean isEdited;
    private EditReason editReason;
    private String editReasonCustomText;
    private Instant editedAt;
    private UUID editedByUserId;

    private boolean isAnonymous;
    private boolean isDeleted;

    private Integer reactionCount;

    private Instant createdAt;
    private Instant updatedAt;

}
