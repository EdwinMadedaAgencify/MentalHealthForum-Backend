package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FocusCategoryRequest(
        @NotNull(message = "Focus Category Id is required")
        UUID categoryId
) {}
