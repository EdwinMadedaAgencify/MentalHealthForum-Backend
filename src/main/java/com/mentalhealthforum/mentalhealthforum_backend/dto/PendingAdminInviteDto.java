package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;

import java.time.Instant;
import java.util.UUID;

public record PendingAdminInviteDto(
        // Primary key used by the application
        UUID userId,

        // Basic Profile Information
        String username,
        String firstName,
        String lastName,
        String email,

        String[] groups,

        // Status and Audit Fields
        boolean enabled,
        boolean emailVerified,

        InviterDto invitedBy,

        Instant createdDate,
        Instant updated_at,

        OnboardingStage currentStage
) {}