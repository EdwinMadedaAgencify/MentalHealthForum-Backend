package com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.PostType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {

    @NotNull(message = "Thread ID is required")
    private UUID threadId;

    private UUID parentPostId; // For threaded replies (one level only)

    private PostType postType;

    @NotNull(message = "Post content is required")
    @Size(min = 1, max = 1000, message = "Content must be between 1 and 10000 characters")
    private String content;

    @Builder.Default
    private ContentWarningType contentWarningType = ContentWarningType.NONE;

    private String contentWarningCustomText;

    @Builder.Default
    private Boolean isAnonymous = false;

    private String customAnonymousName; // if user doesn't like generated animal name
}
