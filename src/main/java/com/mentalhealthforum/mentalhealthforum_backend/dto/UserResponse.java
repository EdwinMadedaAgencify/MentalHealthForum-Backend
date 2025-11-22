package com.mentalhealthforum.mentalhealthforum_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The official DTO returned to clients for displaying user profile information.
 * It calculates the full name and excludes sensitive Keycloak fields, providing
 * a clean contract with the frontend.
 */
@Setter
@Getter
public class UserResponse {

    private UUID userId;  // The Keycloak ID
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String bio;
    private Instant dateJoined;
    private boolean isSelf;

    public UserResponse(
            UUID userId,
            String email,
            String username,
            String firstName,
            String lastName,
            String bio,
            Instant dateJoined,
            boolean isSelf) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.bio = bio;
        this.dateJoined = dateJoined;
        this.isSelf = isSelf;
    }
}
