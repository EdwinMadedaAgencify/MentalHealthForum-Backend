package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * DTO representing the essential authoritative user data extracted from the
 * external Identity Provider's AppUser Representation payload.
 * This is used INTERNALLY within the service layer for mapping and audit.
 */
public record KeycloakUserDto(
        // Primary key used by the application
        String id,

        // Basic Profile Information
        String username,
        String firstName,
        String lastName,
        String email,

        // Status and Audit Fields
        boolean enabled,
        boolean emailVerified,

        // The timestamp needs to be handled carefully, assuming it's milliseconds
        // since epoch as commonly used in Java/Keycloak (1763153069993 in your sample).
        @JsonProperty("createdTimestamp")
        Long createdTimestampMs
) {
    /**
     * Helper method to convert the milliseconds timestamp to a more usable Instant object.
     * @return Instant object representing the user's creation time in Keycloak.
     */
    public Instant getCreatedInstant() {
        return  (createdTimestampMs == null)? null : Instant.ofEpochMilli(createdTimestampMs);
    }
}