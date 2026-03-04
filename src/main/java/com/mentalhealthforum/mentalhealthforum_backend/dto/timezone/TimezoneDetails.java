package com.mentalhealthforum.mentalhealthforum_backend.dto.timezone;

public record TimezoneDetails(
        String id,
        String displayName,
        String offset,
        String region
) {}