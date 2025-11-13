package com.mentalhealthforum.mentalhealthforum_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank
        String refreshToken
) {}
