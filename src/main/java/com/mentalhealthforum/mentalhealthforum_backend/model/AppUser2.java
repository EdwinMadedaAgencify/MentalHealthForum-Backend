package com.mentalhealthforum.mentalhealthforum_backend.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * R2DBC Entity representing the application-specific user profile data.
 * This data is linked to the core identity managed by Keycloak.
 */
@Getter
@Setter
@Table("app_users")
public class AppUser2 {

    // 1. Surrogate Primary Key
    @Id
    private Integer id;

    // 2. IDENTITY LINKAGE (Keycloak's unique UUID)
    @Column("keycloak_id")
    @NotBlank
    @Size(max = 36)
    private String keycloakId;

    // 3. SYNCHRONIZED DATA (Cached email from Keycloak)
    @Email
    @NotBlank
    @Size(max = 255)
    private String email;

    // 4. PUBLIC PROFILE (Application SoT)
    @Column("display_name")
    @NotBlank
    @Size(max = 50)
    private String displayName;

    @Column("profile_bio")
    private String profileBio;

    // 5. FORUM METADATA
    @Column("posts_count")
    @NotNull
    private Integer postsCount = 0;

    @Column("joined_at")
    @NotNull
    private Instant joinedAt;

    // 6. PRIVACY / STATUS
    @Column("prefers_anonymity")
    @NotNull
    private Boolean prefersAnonymity = false;

    @Column("is_active")
    @NotNull
    private Boolean isActive = true;

    // --- Utility Methods ---

    /**
     * Initializes a new user upon first synchronization from Keycloak.
     */
    public static AppUser2 createNew(String keycloakId, String email, String initialDisplayName) {
        AppUser2 user = new AppUser2();
        user.setKeycloakId(keycloakId);
        user.setEmail(email);
        user.setDisplayName(initialDisplayName);
        user.setJoinedAt(Instant.now());
        user.setPostsCount(0);
        user.setIsActive(true);
        user.setPrefersAnonymity(false);
        return user;
    }
}