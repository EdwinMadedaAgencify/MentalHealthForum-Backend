package com.mentalhealthforum.mentalhealthforum_backend.enums;

public enum RestrictionType {
    MUTE, // Can read, cannot post
    POSTING_BAN, // Cannot create threads/posts
    CATEGORY_BAN, // Banned from specific category
    SUSPENSION, // Cannot access forum at all
    PERMANENT_BAN // Permanent ban
}
