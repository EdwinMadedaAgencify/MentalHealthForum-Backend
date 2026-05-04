package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.PendingUserEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface PendingUserRepository extends R2dbcRepository<PendingUserEntity, Long> {
    Mono<PendingUserEntity> findByUsername(String username);
    Mono<PendingUserEntity> findByEmail(String email);

    @Modifying
    @Query("DELETE FROM pending_users WHERE username = :username OR :email = :email")
    Mono<Void> deleteByUsernameOrEmail(String username, String email);

    @Modifying
    @Query("DELETE FROM Pending_users WHERE email NOT IN (SELECT email FORM verification_tokens")
    Mono<Long> deleteOrphanedPendingUsers();
}
