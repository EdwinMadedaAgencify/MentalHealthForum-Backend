package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.mentalhealthforum.mentalhealthforum_backend.enums.EditReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("post_edit_history")
public class PostEditHistoryEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("post_id")
    private UUID postId;

    @Column("previous_content")
    private String previousContent;

    @Column("previous_word_count")
    private Integer previousWordCount;

    @CreatedDate
    @Column("edited_at")
    private Instant editedAt;

    @Column("edited_by")
    private UUID editedBy;

    @Column("edit_reason_type")
    private EditReason editReasonType;

    @Column("edit_reason_custom_text")
    private String editReasonCustomText;

}
