package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.enums.OtpPurpose;
import com.mentalhealthforum.mentalhealthforum_backend.model.OtpCredentialEntity;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface OtpCredentialRepository extends R2dbcRepository<OtpCredentialEntity, Long> {

    Mono<OtpCredentialEntity> findByEmailAndPurpose(String email, OtpPurpose purpose);

    @Modifying
    @Query("DELETE FROM otp_credentials WHERE expiry_date < :now")
    Mono<Long> deleteAllExpired(Instant now);

}
