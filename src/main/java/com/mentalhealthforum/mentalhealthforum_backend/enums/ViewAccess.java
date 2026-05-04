package com.mentalhealthforum.mentalhealthforum_backend.enums;

public enum ViewAccess {
    PUBLIC,           // Anyone can view (even unauthenticated)
    MEMBERS_ONLY,     // Any logged-in member
    VERIFIED_ONLY,    // Only verified users (trusted members)
    MODERATORS_ONLY,  // Only moderators and admins
    ADMINS_ONLY // Only admins
}
