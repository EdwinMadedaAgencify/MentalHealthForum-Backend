package com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BanUserRequest(

    @NotBlank(message = "Ban reason is required")
    @Size(min = 10, max = 500, message = "Ban reason must be between 10 and 500 characters")
    String reason,

    UUID relatedReportId
    
) {}
