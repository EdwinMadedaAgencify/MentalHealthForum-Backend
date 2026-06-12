package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ConnectionStatus;
import com.mentalhealthforum.mentalhealthforum_backend.model.UserConnectEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface UserConnectRepository extends R2dbcRepository<UserConnectEntity, UUID> {

    @Query("""
        SELECT COUNT(*) FROM user_connections
         WHERE (user_1 = :userId OR user_2 = :userId)
         AND status = 'ACCEPTED'
    """)
    Mono<Long> countAcceptedConnections(@Param("userId") UUID userId);

    @Query("""
        SELECT c.* FROM user_connections c
        INNER JOIN app_users other_user ON other_user.keycloak_id =
            CASE
                WHEN c.user_1 = :userId THEN c.user_2
                ELSE c.user_1
            END
        WHERE (c.user_1 = :userId OR c.user_2 = :userId)
            AND c.status = 'ACCEPTED'
            AND (:search IS NULL OR
                LOWER(other_user.display_name) LIKE '%' || LOWER(:search) || '%')
        ORDER BY
            CASE :sortDirection
                WHEN 'DESC' THEN
                    CASE :sortBy
                        WHEN 'created_at' THEN c.created_at::text
                        WHEN 'display_name' THEN other_user.display_name
                        ELSE c.created_at::text
                    END
                ELSE NULL
            END DESC NULLS LAST,
            CASE :sortDirection
                WHEN 'ASC' THEN
                    CASE :sortBy
                        WHEN 'created_at' THEN c.created_at::text
                        WHEN 'display_name' THEN other_user.display_name
                        ELSE c.created_at::text
                    END
                ELSE NULL
            END ASC NULLS FIRST
            LIMIT :limit OFFSET :offset
    """)
    Flux<UserConnectEntity> findAcceptedConnectionsPaginated(
        @Param("userId") UUID userId,
        @Param("search") String search,
        @Param("sortBy") String sortBy,
        @Param("sortDirection") String sortDirection,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query("""
        SELECT COUNT(*) FROM user_connections c
        INNER JOIN app_users other_user ON other_user.keycloak_id =
            CASE
                WHEN c.user_1 = :userId THEN c.user_2
                ELSE c.user_1
            END
        WHERE (c.user_1 = :userId OR c.user_2 = :userId)
            AND c.status = 'ACCEPTED'
            AND (:search IS NULL OR
                LOWER(other_user.display_name) LIKE '%' || LOWER(:search) || '%')
    """)
    Mono<Long> countAcceptedConnectionsWithFilters(
            @Param("userId") UUID userId,
            @Param("search") String search
    );

    @Query("""
        SELECT c.* FROM user_connections c
        INNER JOIN app_users u ON u.keycloak_id =
            CASE
                WHEN c.user_1 = :userId THEN c.user_2
                ELSE c.user_1
            END
        WHERE (c.user_1 = :userId OR c.user_2 = :userId)
            AND c.status = 'PENDING'
            -- Dynamic filter based on request type
            AND (
                (:filter = 'INCOMING' AND c.initiated_by != :userId) OR
                (:filter = 'OUTGOING' AND c.initiated_by = :userId) OR
                (:filter = 'ALL')
            )
            AND (:search IS NULL OR
                LOWER(u.display_name) LIKE '%' || LOWER(:search) || '%')
        ORDER BY
            CASE :sortDirection
                WHEN 'DESC' THEN
                    CASE :sortBy
                        WHEN 'created_at' THEN c.created_at::text
                        WHEN 'display_name' THEN u.display_name
                        ELSE c.created_at::text
                    END
                ELSE NULL
            END DESC NULLS LAST,
            CASE :sortDirection
                WHEN 'ASC' THEN
                    CASE :sortBy
                        WHEN 'created_at' THEN c.created_at::text
                        WHEN 'display_name' THEN u.display_name
                        ELSE c.created_at::text
                    END
                ELSE NULL
            END ASC NULLS FIRST
            LIMIT :limit OFFSET :offset
    """)
    Flux<UserConnectEntity> findPendingRequestsPaginated(
            @Param("userId") UUID userId,
            @Param("search") String search,
            @Param("filter") String filter,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
        SELECT COUNT(*) FROM user_connections c
        INNER JOIN app_users u ON u.keycloak_id =
            CASE
                WHEN c.user_1 = :userId THEN c.user_2
                ELSE c.user_1
            END
        WHERE (c.user_1 = :userId OR c.user_2 = :userId)
            AND c.status = 'PENDING'
             -- Dynamic filter based on request type
            AND (
                (:filter = 'INCOMING' AND c.initiated_by != :userId) OR
                (:filter = 'OUTGOING' AND c.initiated_by = :userId) OR
                (:filter = 'ALL')
            )
            AND (:search IS NULL OR
                LOWER(u.display_name) LIKE '%' || LOWER(:search) || '%')
    """)
    Mono<Long> countPendingRequestsWithFilters(
            @Param("userId") UUID userId,
            @Param("search") String search,
            @Param("filter") String filter
    );

    @Query("""
        DELETE FROM user_connections
        WHERE status = 'DECLINED'
        AND updated_at < NOW() - (:days || ' days')::INTERVAL
    """)
    Mono<Integer> deleteDeclinedOlderThan(@Param("days") int days);

    @Query("SELECT * FROM user_connections WHERE user_1 = :user1 AND user_2 = :user2")
    Mono<UserConnectEntity> findByUser1AndUser2(@Param("user1") UUID user1, @Param("user2") UUID user2);

    @Query("DELETE FROM user_connections WHERE user_1 = :user1 AND user_2 = :user2")
    Mono<Void> deleteByUser1AndUser2(@Param("user1") UUID user1, @Param("user2") UUID user2);

    @Query("""
        SELECT COUNT(*) FROM user_connections c
        WHERE (c.user_1 = :userId OR c.user_2 = :userId)
            AND c.status = :status
            AND c.initiated_by != :userId -- Only incoming requests
    """)
    Mono<Long> countIncomingRequests(@Param("userId") UUID userId, @Param("status") ConnectionStatus status);
}
