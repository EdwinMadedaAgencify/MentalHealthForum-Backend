package com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows;

import com.mentalhealthforum.mentalhealthforum_backend.enums.WarningType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UserWarningResponse {

    private UUID id;
    private UUID userId;
    private UUID warnedBy;
    private String warnedByDisplayName;
    private WarningType warningType;
    private String warningText;
    private UUID relatedPostId;
    private UUID relatedThreadId;
    private UUID relatedReportId;
    private Instant warnedAt;
    private Instant acknowledgedAt;
    private Instant expiresAt;
    private Boolean isActive;

}
