package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JwtResponse(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("expires_in")
        long expiresIn,

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("refresh_expires_in")
        long refreshExpiresIn,

        @JsonProperty("token_type")
        String tokenType
) {}
