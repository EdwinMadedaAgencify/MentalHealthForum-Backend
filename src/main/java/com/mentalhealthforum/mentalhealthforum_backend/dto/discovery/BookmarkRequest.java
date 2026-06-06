package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BookmarkRequest(
    @NotNull(message = "Thread Id is required")
    UUID threadId,

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes
) {}
