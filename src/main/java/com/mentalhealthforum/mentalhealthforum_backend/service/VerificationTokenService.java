package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.enums.VerificationType;
import com.mentalhealthforum.mentalhealthforum_backend.model.VerificationTokenEntity;
import reactor.core.publisher.Mono;

public interface VerificationTokenService {
    /**
     * Generates a secure token, saves it to R2DBC, and returns the entity.
     */
    Mono<VerificationTokenEntity> generateToken(String email, VerificationType type, String groupPath, String newValue);

    Mono<Void> checkRateLimit(String email);

    Mono<VerificationTokenEntity> findByEmail(String email);

    Mono<VerificationTokenEntity> findTokenByEmailAndType(String email, VerificationType type);

    /**
     *  Validates a token and returns the verification context.
     */
    Mono<VerificationTokenEntity> findAndValidateToken(String token, String email);

    /**
     * Removes a token after successful use.
     */
    Mono<Void> removeToken(Long id);
    Mono<Void> removeToken(String email);
}
