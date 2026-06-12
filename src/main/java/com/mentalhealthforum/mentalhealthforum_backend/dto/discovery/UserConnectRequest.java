package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UserConnectRequest(
        @NotNull(message = "User Id is required")
        UUID userId
) {}
