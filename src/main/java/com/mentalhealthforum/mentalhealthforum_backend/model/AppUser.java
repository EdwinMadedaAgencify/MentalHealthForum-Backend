package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.mentalhealthforum.mentalhealthforum_backend.dto.notification.NotificationPreferences;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ProfileVisibility;
import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;
import com.mentalhealthforum.mentalhealthforum_backend.service.PrivilegedUser;
//import com.mentalhealthforum.mentalhealthforum_backend.utils.JsonUtils;
import com.mentalhealthforum.mentalhealthforum_backend.service.OnboardingProfileData;
import com.mentalhealthforum.mentalhealthforum_backend.utils.JsonUtils;
import com.mentalhealthforum.mentalhealthforum_backend.validation.ValidEmail;
import com.mentalhealthforum.mentalhealthforum_backend.validation.bio.ValidBio;
import com.mentalhealthforum.mentalhealthforum_backend.validation.displayName.ValidDisplayName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.firstName.ValidFirstName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.lastName.ValidLastName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.url.ValidUrl;
import com.mentalhealthforum.mentalhealthforum_backend.validation.username.ValidUsername;
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
 * - notificationPreferences is placed in JSOB format as a setting.
 */

@Setter
@Getter
@Table("app_users")
public class AppUser implements PrivilegedUser, OnboardingProfileData {

    @Id
    @Column("id")
    private UUID id; // DB-generated UUID Primary Key

    // --- Keycloak-Owned Identity Data (Requires Synchronization) ---

    @Column("keycloak_id")
    @NotNull
    private UUID keycloakId; // External identifier provided by Keycloak

    // --- Authoritative Keycloak-synced identity fields ---
    @Column("email")
    @ValidEmail
    private String email;

    @Column("username")
    @ValidUsername
    private String username;

    @Column("first_name")
    @ValidFirstName
    private String firstName;

    @Column("last_name")
    @ValidLastName
    private String lastName;

    // --- Cached/display-only fields (Postgres-compatible storage: text[] or jsonb) ---
    @Column("roles")
    private Set<String> roles = new HashSet<>();

    @Column("groups")
    private Set<String> groups = new HashSet<>();

    // -- Others also synced from keycloak
    @Column("is_enabled")
    private Boolean isEnabled = true;

    @Column("last_synced_at")
    private Instant lastSyncedAt;

    @Column("date_joined")
    private Instant dateJoined;

    // --- Application-specific profile data (Source of Truth is our database) ---

    @Column("display_name")
    @ValidDisplayName
    private String displayName; // Optional public-facing alias; can be included in token

    @Column("avatar_url")
    @ValidUrl
    private String avatarUrl; // Default handled via constant/config; can be null

    @Column("bio")
    @ValidBio
    private String bio;

    @Column("timezone")
    private String timezone = "UTC";

    @Column("language")
    private String language = "en";

    @Column("profile_visibility")
    private ProfileVisibility profileVisibility = ProfileVisibility.MEMBERS_ONLY;

    @Column("support_role")
    private SupportRole supportRole = SupportRole.NOT_SPECIFIED;

    @Column("notification_preferences")
    private JsonNode notificationPreferencesJson;

    @Column("posts_count")
    @Min(0)
    private Integer postsCount = 0;


    @Column("reputation_score")
    @DecimalMin("0.0")
    private Double reputationScore = 0.0;

    @Column("last_active_at")
    private Instant lastActiveAt;

    @Column("last_posted_at")
    private Instant lastPostedAt;

    @Column("is_active")
    private Boolean isActive = true;

    @Column("account_deletion_requested_at")
    private Instant accountDeletionRequestedAt;

    // --- Transient / helper fields ---
    @Transient
    private boolean isSelf = false;

    @Transient
    private String pendingEmail;

    @Transient
    public String getInitials(){

        if (this.firstName != null && this.lastName != null &&
                !this.firstName.isBlank() && !this.lastName.isBlank()) {
            return (firstName.charAt(0) + "" + lastName.charAt(0)).toUpperCase();
        }

        if (this.username != null && !this.username.isBlank()) {
            return String.valueOf(username.charAt(0)).toUpperCase();
        }

        return "??";
    }

    // --- Constructors ---

    public AppUser() {
        this.dateJoined = Instant.now();
        //this.bio = "Hi, I'm here to connect, learn and grow. Let's support each other!";
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
        this(); // Call default constructor to set defaults
        if(keycloakStringId == null || keycloakStringId.trim().isEmpty()){
            throw new IllegalArgumentException("keycloakStringId cannot be null or empty");
        }
        this.keycloakId = UUID.fromString(keycloakStringId);
        this.email = email;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
    }


    // --- NotificationPreferences getter/setter using JsonUtils ---
    public NotificationPreferences getNotificationPreferences() {
        if(notificationPreferencesJson == null || notificationPreferencesJson.isEmpty()){
            return new NotificationPreferences();
        }
        return JsonUtils.jsonNodeToObject(notificationPreferencesJson, NotificationPreferences.class);
    }

    public void setNotificationPreferences(NotificationPreferences prefs){
        this.notificationPreferencesJson = JsonUtils.objectToJsonNode(
                prefs == null ? new NotificationPreferences() : prefs
        );
    }

    @Override
    public String displayName() {
        return this.displayName;
    }

    @Override
    public String bio() {
        return this.bio;
    }

    @Override
    public String timezone() {
        return this.timezone;
    }

    @Override
    public SupportRole supportRole() {
        return this.supportRole;
    }
}
