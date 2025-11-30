package com.mentalhealthforum.mentalhealthforum_backend.enums;

/**
 * Defines the user's primary purpose on the forum, mapping to the support_role_enum in PostgreSQL.
 * This is used for R2DBC mapping of the 'support_role' column.
 */
public enum SupportRole {
    NOT_SPECIFIED, SEEKING_SUPPORT, OFFERING_SUPPORT, BOTH
}


//public enum SupportRole {
//    NOT_SPECIFIED,      // Default: user hasn't chosen yet or prefers not to say
//    SEEKING_SUPPORT,    // "I'm here to find support"
//    OFFERING_SUPPORT,   // "I'm here to help others"
//    BOTH                // "I seek and offer support"
//}