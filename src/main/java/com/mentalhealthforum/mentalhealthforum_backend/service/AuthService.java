package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.JwtResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.LoginRequest;
import reactor.core.publisher.Mono;

public interface AuthService {
    Mono<JwtResponse> authenticate(LoginRequest request);
    Mono<JwtResponse> refreshTokens(String refreshToken);

    /**
     * Logs out the user by revoking the refresh token in Keycloak,
     * invalidating the session immediately.
     * @param refreshToken The refresh token to be revoked.
     * @return Mono<Void> indicating the operation is complete (success or handled failure).
     */
    Mono<Void> logout(String refreshToken);
}
