package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Reactive Repository for the application's internal AppUser profiles (R2DBC).
 */
public interface AppUserRepository extends R2dbcRepository<AppUser, String> {

    Mono<AppUser> findAppUserByKeycloakId(String keycloakId);

    @Modifying
    @Query("""
        UPDATE app_users
        SET email = :email,
            WHERE keycloak_id = :keycloakId
    """)
   Mono<Void> updateLocalEmail(UUID keycloakId, String email);

    @Query("""
            SELECT * FROM app_users
            WHERE (:isActive IS NULL OR is_active = :isActive)
              AND (:role IS NULL OR :role = ANY(roles))
              AND (:groups IS NULL OR groups && :groups)
              AND (:search IS NULL OR LOWER(display_name) LIKE '%' || LOWER(:search) || '%')
            ORDER BY
                -- 1. Current user first
                CASE WHEN :currentUserId IS NOT NULL AND :currentUserFirst = true
                    AND keycloak_id = :currentUserId::uuid
                         THEN 0 ELSE 1 END,
           
                -- 2. Sort by selected field and direction: DESC (ALL CAST TO TEXT to fix type mismatch)
                CASE :sortDirection
                    WHEN 'DESC' THEN
                        CASE :sortBy
                            WHEN 'date_joined' THEN date_joined::text
                            WHEN 'posts_count' THEN LPAD(posts_count::text, 10, '0')
                            WHEN 'reputation_score' THEN LPAD(TO_CHAR(reputation_score, 'FM999999.99'), 15, '0')
                            WHEN 'last_posted_at' THEN last_posted_at::text
                            WHEN 'last_active_at' THEN last_active_at::text
                            WHEN 'display_name' THEN COALESCE(NULLIF(display_name, ''), 'zzzzzzzz')
                            ELSE NULL
                        END
                    ELSE NULL
                END DESC NULLS LAST,
           
                -- 3. Sort by selected field and direction: ASC (ALL CAST TO TEXT to fix type mismatch)
                CASE :sortDirection
                    WHEN 'ASC' THEN
                        CASE :sortBy
                            WHEN 'date_joined' THEN date_joined::text
                            WHEN 'posts_count' THEN LPAD(posts_count::text, 10, '0')
                            WHEN 'reputation_score' THEN LPAD(TO_CHAR(reputation_score, 'FM999999.99'), 15, '0')
                            WHEN 'last_posted_at' THEN last_posted_at::text
                            WHEN 'last_active_at' THEN last_active_at::text
                            WHEN 'display_name' THEN COALESCE(NULLIF(display_name, ''), 'zzzzzzzz')
                            ELSE NULL
                        END
                    ELSE NULL
                END ASC NULLS FIRST,
           
                -- 4. Tie breaker for deterministic ordering
                keycloak_id
            LIMIT :limit OFFSET :offset;
           """)
    Flux<AppUser> findAllPaginated(
            @Param("isActive") Boolean isActive,
            @Param("role") String role,
            @Param("groups") String[] groups,
            @Param("currentUserId") String currentUserId,
            @Param("currentUserFirst") boolean currentUserFirst,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("search") String search,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
            SELECT COUNT(*) FROM app_users
            WHERE (:isActive IS NULL OR is_active = :isActive)
            AND (:role IS NULL OR :role = ANY(roles))
            AND (:groups IS NULL OR groups && :groups)
            """)
    Mono<Long> countAll(
            @Param("isActive") Boolean isActive,
            @Param("role") String role,
            @Param("groups") String[] groups
    );
}
