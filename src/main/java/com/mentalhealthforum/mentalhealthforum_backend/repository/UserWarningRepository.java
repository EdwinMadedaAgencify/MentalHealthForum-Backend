package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.UserWarningEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface UserWarningRepository extends R2dbcRepository<UserWarningEntity, UUID> {

    Flux<UserWarningEntity> findByUserIdOrderByWarnedAtDesc(UUID userId);

    Flux<UserWarningEntity> findByUserIdAndIsActiveTrue(UUID userId);

    Mono<Long> countByUserIdAndIsActiveTrue(UUID userId);

    @Query("UPDATE user_warnings SET is_active = false, expires_at = NOW() WHERE id = :warningId")
    Mono<Void> deactivateWarning(@Param("warningId") UUID warningId);

    @Query("UPDATE user_warnings SET acknowledged_at = NOW() WHERE id = :warningId")
    Mono<Void> acknowledgeWarning(@Param("warningId") UUID warningId);

}
