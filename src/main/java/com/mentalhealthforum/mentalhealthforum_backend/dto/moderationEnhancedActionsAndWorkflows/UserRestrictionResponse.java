package com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows;

import com.mentalhealthforum.mentalhealthforum_backend.enums.RestrictionType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.WarningType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UserRestrictionResponse {

    private UUID id;
    private UUID userId;
    private RestrictionType restrictionType;
    private String reason;
    private UUID imposedBy;
    private String imposedByDisplayName;
    private UUID relatedReportId;
    private UUID relatedThreadId;
    private UUID restrictedCategoryId;
    private Instant startsAt;
    private Instant expiresAt;
    private Boolean isActive;
    private Instant liftedAt;
    private UUID liftedBy;
    private String liftReason;

}
