package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.timezone.TimezoneDetails;
import com.mentalhealthforum.mentalhealthforum_backend.dto.timezone.TimezonesResponse;

public interface TimezoneService {
    TimezonesResponse getTimezonesGrouped();
    TimezonesResponse getTimezonesFlat();

    TimezoneDetails getTimezoneDetails(String zoneId);

    String canonicalizeTimezone(String timezone);
}
