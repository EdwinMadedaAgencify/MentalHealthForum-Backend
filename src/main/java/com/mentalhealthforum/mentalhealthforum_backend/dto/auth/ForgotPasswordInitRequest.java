package com.mentalhealthforum.mentalhealthforum_backend.dto.auth;

import com.mentalhealthforum.mentalhealthforum_backend.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordInitRequest(
        @NotBlank(message = "Email is required.")
        @ValidEmail
        String email
) {}
