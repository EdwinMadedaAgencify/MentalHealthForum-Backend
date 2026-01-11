package com.mentalhealthforum.mentalhealthforum_backend.dto.novu;

import com.mentalhealthforum.mentalhealthforum_backend.service.NovuPayload;

import java.util.Map;

public record InvitationLinkRenewalPayload(
        String firstName,
        String verificationLink
) implements NovuPayload {
    @Override
    public Map<String, Object> toPayloadMap() {
        return Map.of(
                "firstName", firstName,
                "verificationLink", verificationLink
        );
    }
}
