package com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.timezone;

public record TimezoneDetails(
        String id,
        String displayName,
        String offset,
        String region
) {}