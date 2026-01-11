package com.mentalhealthforum.mentalhealthforum_backend.dto.novu;

import com.mentalhealthforum.mentalhealthforum_backend.service.NovuPayload;

import java.util.Map;

public record AppUserVerificationPayload(
        String firstName,
        String verificationLink,
        boolean isNew
) implements NovuPayload {
    @Override
    public Map<String, Object> toPayloadMap() {
        return Map.of(
                "firstName", firstName,
                "verificationLink", verificationLink,
                "isNewUser", isNew
        );
    }
}
