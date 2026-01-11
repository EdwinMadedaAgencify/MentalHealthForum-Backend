package com.mentalhealthforum.mentalhealthforum_backend.dto.novu;

import com.mentalhealthforum.mentalhealthforum_backend.service.NovuPayload;

import java.util.Map;

public record OtpPayload(String code) implements NovuPayload {
    @Override
    public Map<String, Object> toPayloadMap() {
        return Map.of("otp_code", code);
    }
}
