package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Setter
@Getter
@NoArgsConstructor
@Table(name = "admin_invitations")
public class AdminInvitation{
    @Id
    @Column("id")
    private UUID id; // DB-generated UUID Primary Key

    // --- Keycloak-Owned Identity Data (Requires Synchronization) ---

    @Column("keycloak_id")
    @NotNull
    private UUID keycloakId; // External identifier provided by Keycloak

    // --- Authoritative Keycloak-synced identity fields ---
    @Email
    @Column("email")
    @NotBlank
    private String email;

    @Column("username")
    @NotBlank
    private String username;

    @Column("first_name")
    @NotBlank
    private String firstName;

    @Column("last_name")
    @NotBlank
    private String lastName;

    // --- Cached/display-only fields (Postgres-compatible storage: text[] or jsonb) ---
    @Column("groups")
    private Set<String> groups = new HashSet<>();

    // -- Others also synced from keycloak
    @Column("is_enabled")
    private Boolean isEnabled = true;

    @Column("is_email_verified")
    private Boolean isEmailVerified = false;

    @Column("date_created")
    private Instant dateCreated;

    // --- Application-specific data

    @Column("invited_by")
    private UUID invitedBy;

    @Column("updated_at")
    private Instant updatedAt = Instant.now();

    @Column("current_stage")
    private OnboardingStage currentStage = OnboardingStage.AWAITING_VERIFICATION;

    @Column("is_initial_login")
    private Boolean isInitialLogin = true;

    public AdminInvitation(
            String keycloakStringId,
            String email,
            String username,
            String firstName,
            String lastName,
            Set<String> groups,
            Instant dateCreated,
            String invitedByStringId
    ) {
        if(keycloakStringId == null || keycloakStringId.trim().isEmpty()){
            throw new IllegalArgumentException("keycloakStringId cannot be null or empty");
        }
        if(invitedByStringId == null || invitedByStringId.trim().isEmpty()){
            throw new IllegalArgumentException("invitedByStringId cannot be null or empty");
        }

        this.keycloakId = UUID.fromString(keycloakStringId);
        this.email = email;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.groups = groups;
        this.dateCreated = dateCreated;
        this.invitedBy = UUID.fromString(invitedByStringId);
    }
}
