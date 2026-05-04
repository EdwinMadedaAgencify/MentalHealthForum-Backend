package com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank
        String refreshToken
) {}
