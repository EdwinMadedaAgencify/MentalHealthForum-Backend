package com.mentalhealthforum.mentalhealthforum_backend.dto.user;

public record ProfileUpdateResult(
        KeycloakUserDto keycloakUserDto,
        String pendingEmail // Null if no change
) {}
