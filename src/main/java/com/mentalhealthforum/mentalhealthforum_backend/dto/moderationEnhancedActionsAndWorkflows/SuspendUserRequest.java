package com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record SuspendUserRequest(

    @NotBlank(message = "Suspension reason is required")
    @Size(min = 10, max = 500, message = "Suspend reason must be between 10 and 500 characters")
    String reason,

    @NotNull(message = "Duration in days is required.")
    @Positive(message = "Duration must be positive")
    @Max(value = 365, message = "Duration cannot exceed 365 days")
    Integer durationDays,

    UUID relatedReportId
    
) {}
