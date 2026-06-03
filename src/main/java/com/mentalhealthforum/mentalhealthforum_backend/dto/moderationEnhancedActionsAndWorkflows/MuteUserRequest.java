package com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows;

import com.mentalhealthforum.mentalhealthforum_backend.enums.WarningType;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record MuteUserRequest(

    @NotBlank(message = "Mute reason is required")
    @Size(min = 10, max = 500, message = "Mute reason must be between 10 and 500 characters")
    String reason,

    @NotNull(message = "Duration in hours is required.")
    @Positive(message = "Duration must be positive")
    @Max(value = 720, message = "Duration cannot exceed 720 hours (30 days)")
    Integer durationHours,

    UUID relatedReportId

) {}
