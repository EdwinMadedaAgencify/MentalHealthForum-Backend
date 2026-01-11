package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.enums.VerificationType;
import com.mentalhealthforum.mentalhealthforum_backend.model.VerificationToken;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface VerificationTokenRepository extends R2dbcRepository<VerificationToken, Long> {

    Mono<VerificationToken> findByEmail(String email);

    // Using a custom query for Safety, or let Spring derive it:
    Mono<VerificationToken> findByTokenAndEmail(String token, String email);

    Mono<VerificationToken> findByEmailAndType(String email, VerificationType type);

    Mono<Void> deleteByEmail(String email);

    Mono<Void> deleteByEmailAndType(String email, VerificationType type);

    @Modifying
    @Query("DELETE FROM verification_tokens WHERE expiry_date < :now")
    Mono<Long> deleteAllExpired(Instant now);
}
