package com.mentalhealthforum.mentalhealthforum_backend.model;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * R2DBC Entity representing the internal application user profile.

 * Stores Keycloak identity data (for fast lookups via sync) and application-specific data.

 * Roles and Groups are cached/display-only fields for frontend/backoffice use.
 * They are NOT authoritative for authorization decisions — Keycloak JWT claims remain the source of truth.

 * Note:
 * - keycloakId (UUID) is the primary key.
 * - avatarUrl default handled via application constant/config; can be null.
 * - notificationPreferences will eventually be normalized into a separate table with a join table for user selections.
 */

@Setter
@Getter
@Table("app_users")
public class AppUser {

    @Id
    @Column("id")
    private UUID id; // DB-generated UUID Primary Key

    @Column("keycloak_id")
    @NotNull
    private UUID keycloakId; // External identifier provided by Keycloak (business key, UUID)

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
    @Column("roles")
    private Set<String> roles = new HashSet<>();

    @Column("groups")
    private Set<String> groups = new HashSet<>();

    // --- Application-specific profile data (Source of Truth is our database) ---

    @Column("display_name")
    @Size(max = 100)
    private String displayName; // Optional public-facing alias; can be included in token

    @Column("bio")
    private String bio;

    @Column("date_joined")
    private Instant dateJoined;

    @Column("avatar_url")
    private String avatarUrl; // Default handled via constant/config; can be null

    @Column("last_active_at")
    private Instant lastActiveAt;

    @Column("posts_count")
    @Min(0)
    private Integer postsCount = 0;

    @Column("reputation_score")
    @DecimalMin("0.0")
    @DecimalMax("5.0")
    private Double reputationScore = 0.0;

    @Column("is_enabled")
    private Boolean isEnabled = true;

    @Column("prefers_anonymity")
    private Boolean prefersAnonymity = false;

    @Column("timezone")
    private String timezone = "UTC";

    @Column("language")
    private String language = "en";

    /**
     * Placeholder for notification preferences.
     * In the future, this should be normalized to a join table:
     * user_notification_preferences(keycloak_id, preference_id, enabled)
     */
    @Column("notification_preferences")
    private Set<String> notificationPreferences = new HashSet<>();

    @Column("last_posted_at")
    private Instant lastPostedAt;

    // --- Transient / helper fields ---
    @Transient
    private boolean isSelf = false;

    // --- Constructors ---

    public AppUser() {
        this.dateJoined = Instant.now();
        this.bio = "Hi, I'm here to connect, learn and grow. Let's support each other!";
    }

    /**
     * Constructor accepting Keycloak string ID, immediately converting to UUID.
     *
     * @param keycloakStringId Keycloak user ID as string
     * @param email            Email
     * @param username         Username
     * @param firstName        First name
     * @param lastName         Last name
     */
    public AppUser(
            String keycloakStringId,
            String email,
            String username,
            String firstName,
            String lastName
    ) {
        this();
        this.keycloakId = UUID.fromString(keycloakStringId); // Direct conversion here
        this.email = email;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
