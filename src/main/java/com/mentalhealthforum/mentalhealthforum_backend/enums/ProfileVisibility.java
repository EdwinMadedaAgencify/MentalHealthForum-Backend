package com.mentalhealthforum.mentalhealthforum_backend.enums;

/**
 * Defines the visibility levels for user profiles on the forum, mapping to the profile_visibility_enum in PostgreSQL.
 * This is used for R2DBC mapping of the 'profile_visibility' column.
 */
public enum ProfileVisibility {
    MEMBERS_ONLY,  // Default: Only logged-in members can see
    PRIVATE        // Only user themselves (or admins)
}
