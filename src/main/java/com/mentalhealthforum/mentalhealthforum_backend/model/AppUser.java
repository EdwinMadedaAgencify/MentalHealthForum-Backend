package com.mentalhealthforum.mentalhealthforum_backend.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * R2DBC Entity representing the internal application user profile.
 * Stores Keycloak identity data (for fast lookups via sync) and application-specific data.
 */

@Setter
@Getter
@Table("app_users")
public class AppUser {

    // --- Getters and Setters (Omitted for brevity, but required) ---
    // The Keycloak ID serves as the Primary Key in our application database.
    @Id
    private Long id; // Auto-incremented, internal identifier for database management

    @Column("keycloak_id")
    @NotBlank
    private String keycloakId; // External identifier provided by Keycloak (business key)

    // Authoritative data synced from Keycloak's userinfo endpoint
    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String username;

    @Column("first_name")
    @NotBlank
    private String firstName;

    @Column("last_name")
    @NotBlank
    private String lastName;

    // Application-specific data (Source of Truth is our database)
    private String bio;

    @Column("date_joined")
    private Instant dateJoined;

    // Transient field for the getAllUsers endpoint logic (isSelf flag).
    @Transient
    private boolean isSelf = false;

    // --- Constructors ---

    public AppUser(){
        this.dateJoined = Instant.now();
        this.bio = "Hi, I'm here to connect, learn and grow. Let's support each other!";
    }

    public AppUser(
            String keycloakId,
            String email,
            String username,
            String firstName,
            String lastName
    ){
        this();
        this.keycloakId = keycloakId;
        this.email = email;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    // Future application-specific fields (e.g., postCount, reputationScore)
    // can be added here once the requirements are defined.
}

