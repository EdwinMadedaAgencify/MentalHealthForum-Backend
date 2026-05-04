package com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.timezone;

import java.util.List;
import java.util.Map;

public record TimezonesResponse(
        Map<String, List<TimezoneDetails>> grouped,
        List<TimezoneDetails> flat,
        long Total
) {}
