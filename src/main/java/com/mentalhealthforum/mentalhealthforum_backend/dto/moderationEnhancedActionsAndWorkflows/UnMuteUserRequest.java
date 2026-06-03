package com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnMuteUserRequest(
    @NotBlank(message = "Unmute reason is required")
    @Size(min = 10, max = 500, message = "Ban reason must be between 10 and 500 characters")
    String reason
) {}
