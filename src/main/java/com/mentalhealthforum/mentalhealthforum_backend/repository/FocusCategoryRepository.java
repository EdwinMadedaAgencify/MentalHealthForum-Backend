package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.FocusCategoryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface FocusCategoryRepository extends R2dbcRepository<FocusCategoryEntity, UUID> {

    Mono<Boolean> existsByUserIdAndCategoryId(UUID userId, UUID categoryId);

    Mono<Void> deleteByUserIdAndCategoryId(UUID userId, UUID categoryId);

    Flux<FocusCategoryEntity> findByUserId(UUID userId);

    Mono<Long> countByUserId(UUID userId);

    @Query("""
        SELECT fc.* FROM focus_categories fc
        INNER JOIN forum_categories C ON fc.category_id = c.id
        WHERE fc.user_id = :userId
            AND (:search IS NULL OR
                 LOWER(c.name) LIKE '%' || LOWER(:search) || '%' OR
                 LOWER(c.description) LIKE '%' || LOWER(:search) || '%'
                )
            AND (:notificationEnabled IS NULL OR fc.notification_enabled = :notificationEnabled)
        ORDER BY
            CASE :sortDirection
                WHEN 'DESC' THEN
                    CASE :sortBy
                        WHEN 'created_at' THEN fc.created_at::text
                        WHEN 'category_name' THEN c.name
                        ELSE fc.created_at::text
                    END
                ELSE NULL
            END DESC NULLS LAST,
    
              CASE :sortDirection
                WHEN 'ASC' THEN
                    CASE :sortBy
                        WHEN 'created_at' THEN fc.created_at::text
                        WHEN 'category_name' THEN c.name
                        ELSE fc.created_at::text
                    END
                ELSE NULL
            END ASC NULLS FIRST
        LIMIT :limit OFFSET :offset
    """)
    Flux<FocusCategoryEntity>findPaginatedByUserId(
        @Param("userId") UUID userId,
        @Param("search") String search,
        @Param("notificationEnabled") Boolean notificationEnabled,
        @Param("sortBy") String sortBy,
        @Param("sortDirection") String sortDirection,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query("""
        SELECT COUNT(*) FROM focus_categories fc
        INNER JOIN forum_categories C ON fc.category_id = c.id
        WHERE fc.user_id = :userId
            AND (:search IS NULL OR
                 LOWER(c.name) LIKE '%' || LOWER(:search) || '%' OR
                 LOWER(c.description) LIKE '%' || LOWER(:search) || '%'
                )
            AND (:notificationEnabled IS NULL OR fc.notification_enabled = :notificationEnabled)
    """)
    Mono<Long> countByUserIdWithFilters(
            @Param("userId") UUID userId,
            @Param("search") String search,
            @Param("notificationEnabled") Boolean notificationEnabled
    );



}
