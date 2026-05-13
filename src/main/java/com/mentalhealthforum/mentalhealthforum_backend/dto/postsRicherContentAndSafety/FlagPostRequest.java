package com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety;

import jakarta.validation.constraints.NotBlank;

public record FlagPostRequest(
        @NotBlank(message = "Flag reason is required")
        String reason,

        String details
) {}
