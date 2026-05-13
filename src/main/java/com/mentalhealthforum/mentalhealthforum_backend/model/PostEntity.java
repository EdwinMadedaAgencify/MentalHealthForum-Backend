package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.EditReason;
import com.mentalhealthforum.mentalhealthforum_backend.enums.PostType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("forum_posts")
public class PostEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("thread_id")
    private UUID threadId;

    @Column("author_id")
    private UUID authorId;

    @Column("post_type")
    private PostType postType;

    @Column("parent_post_id")
    private UUID parentPostId;

    @Column("content")
    private String content;

    @Column("word_count")
    private Integer wordCount;

    @Column("content_warning_type")
    private ContentWarningType contentWarningType;

    @Column("content_warning_custom_text")
    private String contentWarningCustomText;

    @Column("flagged_for_review")
    @Builder.Default
    private Boolean flaggedForReview = false;

    @Column("is_edited")
    @Builder.Default
    private Boolean isEdited = false;

    @Column("edit_reason_type")
    private EditReason editReasonType;

    @Column("edit_reason_custom_text")
    private String editReasonCustomText;

    @Column("edited_by_user_id")
    private UUID editedByUserId;

    @Column("is_anonymous")
    @Builder.Default
    private Boolean isAnonymous = false;

    @Column("anonymous_identifier")
    private String anonymousIdentifier;

    @Column("is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column("reaction_count")
    @Builder.Default
    private Integer reactionCount = 0;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private Instant updatedAt;

    // Helper methods
    public boolean isReply() {
        return postType == PostType.REPLY;
    }

    public boolean isAnswer() {
        return  postType == PostType.ANSWER;
    }

    public  boolean isSystemMessage(){
        return postType == PostType.SYSTEM_MESSAGE;
    }

    public boolean isModeratorNote(){
        return postType == PostType.MODERATOR_NOTE;
    }
}
