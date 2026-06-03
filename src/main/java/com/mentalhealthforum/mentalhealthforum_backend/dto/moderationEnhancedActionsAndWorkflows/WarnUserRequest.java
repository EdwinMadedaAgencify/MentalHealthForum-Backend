package com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows;

import com.mentalhealthforum.mentalhealthforum_backend.enums.WarningType;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record WarnUserRequest(

    @NotNull(message = "Warning type is required")
    WarningType warningType,

    @NotBlank(message = "Warning message is required")
    @Size(min = 10, max = 500, message = "Warning text must be between 10 and 500 characters")
    String warningText,

    UUID relatedPostId,

    UUID relatedThreadId,

    UUID relatedReportId,

    @Max(value = 30, message = "Duration cannot exceed 30 days")
    @Min(value = 1, message = "Duration must be at least 1 day")
    Integer expiresInDays

) {}
