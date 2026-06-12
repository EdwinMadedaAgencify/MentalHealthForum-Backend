package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WatchThreadRequest(
        @NotNull(message = "Watch Thread Id is required")
        UUID threadId
) {}
