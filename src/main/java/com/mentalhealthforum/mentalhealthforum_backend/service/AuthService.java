package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.JwtResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.LoginRequest;
import reactor.core.publisher.Mono;

public interface AuthService {
    Mono<JwtResponse> authenticate(LoginRequest request);
    Mono<JwtResponse> refreshTokens(String refreshToken);
}
