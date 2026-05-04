package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.timezone.TimezoneDetails;
import com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.timezone.TimezonesResponse;

public interface TimezoneService {
    TimezonesResponse getTimezonesGrouped();
    TimezonesResponse getTimezonesFlat();

    TimezoneDetails getTimezoneDetails(String zoneId);

    String canonicalizeTimezone(String timezone);
}
