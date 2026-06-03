package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.mentalhealthforum.mentalhealthforum_backend.enums.WarningType;
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
@Table("user_warnings")
public class UserWarningEntity {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("warned_by")
    private UUID warnedBy;

    @Column("warning_type")
    private WarningType warningType;

    @Column("warning_text")
    private String warningText;

    @Column("related_post_id")
    private UUID relatedPostId;

    @Column("related_thread_id")
    private UUID relatedThreadId;

    @Column("related_report_id")
    private UUID relatedReportId;

    @CreatedDate
    @Column("warned_at")
    private Instant warnedAt;

    @Column("acknowledged_at")
    private Instant acknowledgedAt;

    @Column("expires_at")
    private Instant expiresAt;

    @Column("is_active")
    @Builder.Default
    private boolean isActive = true;

}
