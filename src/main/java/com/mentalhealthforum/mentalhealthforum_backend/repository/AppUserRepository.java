package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive Repository for the application's internal AppUser profiles (R2DBC).
 */
public interface AppUserRepository extends R2dbcRepository<AppUser, String> {
    Mono<AppUser> findAppUserByKeycloakId(String keycloakId);
}
