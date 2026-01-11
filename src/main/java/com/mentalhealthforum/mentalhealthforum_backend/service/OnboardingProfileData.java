package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;

/**
 * Common contract for profile information.
 * Allows validation logic to treat DTOs and Database Entities interchangeably.
 */
public interface OnboardingProfileData {
    String displayName();
    String bio();
    String timezone();
    SupportRole supportRole();
}
