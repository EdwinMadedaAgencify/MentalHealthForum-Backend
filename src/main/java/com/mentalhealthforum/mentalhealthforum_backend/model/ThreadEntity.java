package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.mentalhealthforum.mentalhealthforum_backend.dto.threadLifecycleAndMetadata.ThreadSettings;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadStatus;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadType;
import com.mentalhealthforum.mentalhealthforum_backend.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Table("forum_threads")
public class ThreadEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("title")
    private String title;

    @Column("creator_id")
    private UUID creatorId;

    @Column("category_id")
    private  UUID categoryId;

    @Column("thread_type")
    private ThreadType threadType;

    @Column("thread_status")
    private ThreadStatus threadStatus;

    @Column("lock_reason")
    private String lockReason;

    @Column("locked_by")
    private UUID lockedBy;

    @Column("locked_at")
    private Instant lockedAt;

    @Column("lock_expires_at")
    private Instant lockExpiresAt;

    @Column("resolved_at")
    private Instant resolvedAt;

    @Column("resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column("best_answer_post_id")
    private UUID bestAnswerPostId;

    @Column("content_warning_type")
    private ContentWarningType contentWarningType;

    @Column("content_warning_custom_text")
    private String contentWarningCustomText;

    @Column("tags")
    private List<String> tags;

    @Column("is_sticky")
    @Builder.Default
    private Boolean isSticky = false;

    @Column("is_featured")
    @Builder.Default
    private Boolean isFeatured = false;

    @Column("is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column("thread_settings")
    private JsonNode threadSettingsJson;

    @Column("post_count")
    @Builder.Default
    private Integer postCount = 0;

    @Column("view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private Instant updatedAt;

    @Column("last_activity_at")
    private Instant lastActivityAt;

    // --- JSONB Getter/Setter ---
    public ThreadSettings getThreadSettings(){
        if(threadSettingsJson == null || threadSettingsJson.isEmpty()){
            return new ThreadSettings();
        }
        return JsonUtils.jsonNodeToObject(threadSettingsJson, ThreadSettings.class);
    }

    public void setThreadSettings(ThreadSettings threadSettings){
        this.threadSettingsJson = JsonUtils.objectToJsonNode(
                threadSettings == null? new ThreadSettings(): threadSettings
        );
    }

    // Helper methods
    public boolean isOpen(){
        return threadStatus == ThreadStatus.OPEN && !isDeleted;
    }

    public boolean isResolved(){
        return threadStatus == ThreadStatus.RESOLVED;
    }

    public boolean isClosed(){
        return threadStatus == ThreadStatus.CLOSED;
    }

    public boolean isArchived(){
        return threadStatus == ThreadStatus.ARCHIVED;
    }
}
