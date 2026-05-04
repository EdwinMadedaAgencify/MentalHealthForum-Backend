package com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.user;

public record ProfileUpdateResult(
        KeycloakUserDto keycloakUserDto,
        String pendingEmail // Null if no change
) {}
