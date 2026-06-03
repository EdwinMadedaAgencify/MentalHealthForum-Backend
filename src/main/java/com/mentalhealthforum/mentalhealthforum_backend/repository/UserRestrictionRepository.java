package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.enums.RestrictionType;
import com.mentalhealthforum.mentalhealthforum_backend.model.UserRestrictionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface UserRestrictionRepository extends R2dbcRepository<UserRestrictionEntity, UUID> {

    Flux<UserRestrictionEntity> findByUserIdAndIsActiveTrue(UUID userId);

    @Query("""
        SELECT EXISTS(SELECT 1 FROM user_restrictions WHERE user_id = :userId AND restriction_type = :restrictionType AND is_active = true)
    """)
    Mono<Boolean> existsByUserIdRestrictionTypeAndIsActiveTrue(UUID userId, RestrictionType restrictionType);

    @Query("""
        SELECT * FROM user_restrictions
        WHERE user_id = :userId
        AND restriction_type = :restrictionType
        AND is_active = true
        AND (expires_at IS NULL OR expires_at > NOW())
    """)
    Mono<UserRestrictionEntity> findActiveRestrictionByType(
            @Param("userId") UUID userId,
            @Param("restrictionType") RestrictionType restrictionType);

    @Query("""
        UPDATE user_restrictions
        SET is_active = false, lifted_at = NOW(), lifted_by = :liftedBy, lift_reason = :reason
        WHERE id = :restrictionId
    """)
    Mono<Void> liftRestriction(
            @Param("restrictionId") UUID restrictionId,
            @Param("liftedBy") UUID liftedBy,
            @Param("reason") String reason
    );

    @Query("""
        UPDATE user_restrictions
        SET is_active = false
        WHERE is_active = true
        AND expires_at IS NOT NULL
        AND expires_at < NOW()
    """)
    Mono<Integer> deactivateExpiredRestrictions();

}
