package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.mentalhealthforum.mentalhealthforum_backend.enums.RestrictionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_restrictions")
public class UserRestrictionEntity {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("restriction_type")
    private RestrictionType restrictionType;

    @Column("reason")
    private String reason;

    @Column("imposed_by")
    private UUID imposedBy;

    @Column("related_report_id")
    private UUID relatedReportId;

    @Column("restricted_category_id")
    private UUID restrictedCategoryId;

    @Column("starts_at")
    private Instant startsAt;

    @Column("expires_at")
    private Instant expiresAt;

    @Column("is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column("lifted_at")
    private Instant liftedAt;

    @Column("lifted_by")
    private UUID liftedBy;

    @Column("lift_reason")
    private String liftReason;

}
