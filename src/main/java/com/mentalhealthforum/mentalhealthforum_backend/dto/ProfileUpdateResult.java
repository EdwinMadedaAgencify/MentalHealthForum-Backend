package com.mentalhealthforum.mentalhealthforum_backend.dto;

public record ProfileUpdateResult(
        KeycloakUserDto keycloakUserDto,
        String pendingEmail // Null if no change
) {}
