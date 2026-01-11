package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.PendingUser;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface PendingUserRepository extends R2dbcRepository<PendingUser, Long> {
    Mono<PendingUser> findByUsername(String username);
    Mono<PendingUser> findByEmail(String email);

    @Modifying
    @Query("DELETE FROM pending_users WHERE username = :username OR :email = :email")
    Mono<Void> deleteByUsernameOrEmail(String username, String email);

    @Modifying
    @Query("DELETE FROM Pending_users WHERE email NOT IN (SELECT email FORM verification_tokens")
    Mono<Long> deleteOrphanedPendingUsers();
}
