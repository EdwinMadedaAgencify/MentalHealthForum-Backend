package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

/**
 * DTO to capture the authoritative user profile data received from Keycloak's
 * userinfo endpoint (Token Introspection).
 * This data is used to read and sync the user's profile information.
 */
public record UserInfoDto(
        // Keycloak's unique UUID (sub is the standard OIDC claim for user ID)
        @JsonProperty("sub") String keycloakId,

        // Basic Profile Information (Authoritative source in Keycloak)
        String email,
        @JsonProperty("preferred_username") String preferredUsername,
        @JsonProperty("given_name") String givenName,    // maps to names
        @JsonProperty("family_name") String familyName,  // maps to lastName

        // Additional claims (for roles/permissions)
        @JsonProperty("realm_access") RealmAccess realmAccess,

        // Groups the user is a part of
        Set<String> groups
) {
    /**
     * Nested record to capture Keycloak's realm_access claim, which contains roles.
     */
    public record RealmAccess(
            // Roles assigned to the user (e.g., ["forum_member", "trusted_member"])
            Set<String> roles
    ) {}
}