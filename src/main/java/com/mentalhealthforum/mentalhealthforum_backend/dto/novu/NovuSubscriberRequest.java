package com.mentalhealthforum.mentalhealthforum_backend.dto.novu;

import java.util.Map;

public record NovuSubscriberRequest(
        String subscriberId,
        String firstName,
        String lastName,
        String email,
        String avatar,
        String locale,
        Map<String, Object> data
) {}
