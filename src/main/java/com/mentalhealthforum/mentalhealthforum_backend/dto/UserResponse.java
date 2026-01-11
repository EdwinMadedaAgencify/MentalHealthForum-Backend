package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.mentalhealthforum.mentalhealthforum_backend.dto.notification.NotificationPreferences;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ProfileVisibility;
import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The official DTO returned to clients for displaying user profile information.
 * Now includes comprehensive profile data for rich user interfaces.
 */
@Setter
@Getter
public class UserResponse {

    // -- Core Identity ---
    private UUID userId; // The Keycloak ID
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String bio;
    private Instant dateJoined;
    private boolean isSelf;

    // -- Transit Informative Data --
    private String pendingEmail;
    private String initials;

    // --- Enhanced Profile Data ---
    private String displayName;
    private String avatarUrl;
    private String timezone;
    private String language;
    private ProfileVisibility profileVisibility;
    private SupportRole supportRole;

    // --- Engagement Metrics ---
    private Integer postsCount;
    private Double reputationScore;
    private Instant lastActiveAt;
    private Instant lastPostedAt;
    private Boolean isActive;

    // --- Cached Keycloak Data (UI Context) ---
    private Set<String> roles;
    private Set<String> groups;

    // --- User Preferences ---
    private NotificationPreferences notificationPreferences;

    // PROFILE CONSTRUCTOR (Privacy-Respecting) ===
    public UserResponse(
            UUID userId,
            Instant dateJoined,
            boolean isSelf,
            String displayName,
            Integer postsCount,
            Double reputationScore,
            Instant lastActiveAt,
            Instant lastPostedAt,
            Boolean isActive,
            Set<String> roles,
            Set<String> groups
            ) {
        this.userId = userId;
        this.dateJoined = dateJoined;
        this.isSelf = isSelf;
        this.displayName = displayName;
        this.postsCount = postsCount;
        this.reputationScore = reputationScore;
        this.lastActiveAt = lastActiveAt;
        this.lastPostedAt = lastPostedAt;
        this.isActive = isActive;
        this.roles = roles != null ? Set.copyOf(roles) : Set.of();
        this.groups = groups != null ? Set.copyOf(groups) : Set.of();

        // NO NULL SETTING HERE - fields remain uninitialized (null by default)
        // Privacy enforcement happens in the mapper, not here
    }


}
