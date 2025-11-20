package com.mentalhealthforum.mentalhealthforum_backend.dto;

public record UserProfileDto(
        String keycloakId,
        String email,
        String username,
        String firstName,
        String lastName
) {}
