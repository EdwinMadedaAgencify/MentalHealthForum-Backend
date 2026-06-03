package com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnSuspendRequest(
    @NotBlank(message = "Unsuspend Reason is required")
    @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
    String reason
) {}
