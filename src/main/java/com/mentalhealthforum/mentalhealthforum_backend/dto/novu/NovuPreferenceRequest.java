package com.mentalhealthforum.mentalhealthforum_backend.dto.novu;

import java.util.List;

public record NovuPreferenceRequest(
        String templateId, // This matches your Workflow slug like "replies"
        Boolean enabled, // is  workflow enabled
        List<ChannelPreference> preference
) {
    public record ChannelPreference(
            String type, // "in_app" or "email
            Boolean enabled
    ) {}
}
