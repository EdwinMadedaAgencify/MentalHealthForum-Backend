package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * The official DTO returned to clients for displaying user profile information.
 * It calculates the full name and excludes sensitive Keycloak fields, providing
 * a clean contract with the frontend.
 */
public record UserResponse(
        @JsonProperty("userId")
        String userId,

        @JsonProperty("username")
        String username,

        @JsonProperty("email")
        String email,

        @JsonProperty("firstName")
        String firstName,

        @JsonProperty("lastName")
        String lastName,

        @JsonProperty("fullName")
        String fullName, // Derived field combining first and last name

        @JsonProperty("emailVerified")
        boolean emailVerified,

        @JsonProperty("createdAt")
        Instant createdAt
) {}
