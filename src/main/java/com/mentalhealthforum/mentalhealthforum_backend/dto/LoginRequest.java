package com.mentalhealthforum.mentalhealthforum_backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for handling login requests from the custom frontend client.
 */
public record LoginRequest(
        @NotBlank(message = "Username or Email is required.")
        String username,

        @NotBlank(message = "Password is required.")
        String password
) {}
